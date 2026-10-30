package moye.picax.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import moye.picax.MainActivity
import moye.picax.PicaXApplication
import moye.picax.model.Platform
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PicaXApplication; val store = app.store
        val errors = mutableListOf<String>()
        suspend fun task(name: String, action: suspend () -> Unit) {
            try { action() } catch(e: CancellationException) { throw e } catch(e: Exception) { errors += "$name：${e.message}" }
        }
        val last = store.setting("follow.lastChecked").toLongOrNull() ?: 0L
        if(store.bool("follow.enabled") && System.currentTimeMillis() - last >= store.number("follow.minutes", 1440) * 60_000L) task("追更") {
            val changed = FollowUpdates.check(app.repository)
            errors += changed.errors
            if(changed.changed > 0 && store.bool("follow.notifications", true)) notifyUpdates(changed.changed)
        }
        val today = LocalDate.now().toString()
        for(platform in listOf(Platform.picacg, Platform.jmComic)) {
            val key = if(platform == Platform.picacg) "picacg.autoCheckIn" else "jm.autoCheckIn"
            if(store.bool(key) && store.setting("$key.date") != today && store.data.value.accounts.any { it.platform == platform }) task("${platform.title} 签到") {
                app.repository.source(platform).checkIn(); store.set("$key.date", today)
            }
        }
        if(store.bool("webdav.auto") && store.setting("webdav.syncDate") != today) task("WebDAV") {
            WebDavBackup(app.repository).upload(false, false); store.set("webdav.syncDate", today)
        }
        store.set("maintenance.error", errors.joinToString("\n"))
        return if(errors.isEmpty()) Result.success() else Result.retry()
    }

    private fun notifyUpdates(count: Int) {
        if(ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("updates", "收藏更新", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(applicationContext, 12, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(12, NotificationCompat.Builder(applicationContext, "updates").setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("收藏有更新").setContentText("$count 部漫画有新章节").setContentIntent(pending).setAutoCancel(true).build())
    }
    companion object {
        fun schedule(store: AppStore) {
            val enabled = listOf("follow.enabled", "picacg.autoCheckIn", "jm.autoCheckIn", "webdav.auto").any(store::bool)
            val work = WorkManager.getInstance(store.context)
            if(!enabled) { work.cancelUniqueWork("picax-maintenance"); work.cancelUniqueWork("picax-maintenance-now"); return }
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            work.enqueueUniquePeriodicWork("picax-maintenance", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
            work.enqueueUniqueWork("picax-maintenance-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<MaintenanceWorker>().setConstraints(constraints).build())
        }
    }
}
