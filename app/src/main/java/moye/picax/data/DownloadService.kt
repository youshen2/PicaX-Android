package moye.picax.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import moye.picax.MainActivity
import moye.picax.PicaXApplication
import moye.picax.model.*

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private val app get() = application as PicaXApplication
    private val store get() = app.store
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("downloads", "漫画下载", NotificationManager.IMPORTANCE_LOW))
        startForeground(10, notification("准备下载"))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(worker?.isActive != true) worker = scope.launch {
            var notifiedAt = 0L
            val downloader = ChapterDownloader(app.repository) { text ->
                val now = android.os.SystemClock.elapsedRealtime()
                if(now - notifiedAt >= store.number("downloads.notificationSeconds", 2) * 1000L) {
                    notifiedAt = now
                    if(store.bool("downloads.notifications", true)) getSystemService(NotificationManager::class.java).notify(10, notification(text))
                }
            }
            while(true) {
                val batch = store.data.value.downloads.filter { it.status in listOf("等待中", "下载中") }
                    .distinctBy { it.detail.comic.key }.take(store.number("downloads.concurrency", 2).coerceIn(1, 4))
                if(batch.isEmpty()) break
                supervisorScope { batch.map { record -> async { downloader.download(record) } }.awaitAll() }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun notification(text: String) = NotificationCompat.Builder(this, "downloads")
        .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("PicaX 下载").setContentText(text).setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
    override fun onTimeout(startId: Int, fgsType: Int) {
        store.update { it.copy(downloads = it.downloads.map { record -> if(record.status == "下载中") record.copy(status = "已暂停") else record }) }
        worker?.cancel(); stopSelf()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        fun enqueue(context: Context, detail: ComicDetail, chapters: List<Chapter>) {
            val store = (context.applicationContext as PicaXApplication).store
            val blocked = store.setting("downloads.blockChapters").lines().filter { it.isNotBlank() }
            chapters.filterNot { chapter -> blocked.any { chapter.title.contains(it, true) } }.forEach { chapter ->
                val key = "${detail.comic.key}:${chapter.id}"
                val existing = store.data.value.downloads.firstOrNull { it.key == key }
                if(existing?.status != "已完成") store.download(existing?.copy(status = "等待中", error = "") ?: DownloadRecord(detail, chapter, emptyList()))
            }
            resume(context)
        }
        fun resume(context: Context) { context.startForegroundService(Intent(context, DownloadService::class.java)) }
    }
}
