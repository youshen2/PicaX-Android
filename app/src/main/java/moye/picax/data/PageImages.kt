package moye.picax.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import moye.picax.model.Platform
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class PageImages(private val repository: ComicRepository) {
    private val locks = Array(32) { Mutex() }
    private val decoders = Semaphore(2)
    private val ratios = android.util.LruCache<String, Float>(256)
    private val rateLock = Mutex()
    private val writes = java.util.concurrent.atomic.AtomicInteger()
    private val trimLock = Mutex()
    private var nextTransferAt = 0L
    suspend fun file(url: String, platform: Platform, refresh: Boolean = false, limitKiB: Int = 0): File = withContext(Dispatchers.IO) {
        if(url.startsWith("file:")) return@withContext File(java.net.URI(url))
        val path = File(repository.store.context.cacheDir, "pages/${AppStore.safeName(url)}.img")
        locks[(url.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            if(!refresh && path.exists() && path.length() > 0) return@withLock path
            path.parentFile!!.mkdirs()
            val imageUrl = if(platform == Platform.eHentai && java.net.URI(url).path.startsWith("/s/")) {
                val html = repository.http.text(url, platform)
                val document = org.jsoup.Jsoup.parse(html, url)
                val original = if(repository.store.bool("eh.original")) document.selectFirst("a[href*=fullimg.php]")?.absUrl("href") else null
                original?.takeIf { it.isNotBlank() } ?: document.selectFirst("#img")?.absUrl("src")?.takeIf { it.isNotEmpty() } ?: error("图片地址解析失败")
            } else url
            val temporary = File(path.path + ".part")
            try {
                repository.http.response(imageUrl, repository.http.imageHeaders(platform, imageUrl) + ("Referer" to url)).use { response ->
                    response.body!!.byteStream().use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while(true) {
                                val length = input.read(buffer)
                                if(length < 0) break
                                coroutineContext.ensureActive()
                                if(limitKiB > 0) {
                                    val wait = rateLock.withLock {
                                        val now = android.os.SystemClock.elapsedRealtime()
                                        val start = maxOf(now, nextTransferAt)
                                        nextTransferAt = start + length * 1000L / (limitKiB * 1024L)
                                        start - now
                                    }
                                    if(wait > 0) kotlinx.coroutines.delay(wait)
                                }
                                output.write(buffer, 0, length)
                            }
                        }
                    }
                    if(platform == Platform.jmComic) decoders.withPermit {
                        val decoded = BitmapFactory.decodeFile(temporary.path) ?: error("图片解码失败")
                        var restored = decoded
                        try {
                            restored = restoreJm(decoded, url)
                            temporary.outputStream().use { restored.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } finally {
                            if(restored !== decoded) restored.recycle()
                            decoded.recycle()
                        }
                    }
                }
                check(temporary.renameTo(path)) { "无法保存漫画图片" }
                if(writes.incrementAndGet() % 32 == 0) trimCache()
            } finally { temporary.delete() }
            path
        }
    }
    suspend fun trimCache() = withContext(Dispatchers.IO) {
        trimLock.withLock {
            val files = File(repository.store.context.cacheDir, "pages").listFiles()?.filter { it.extension == "img" }.orEmpty()
            var total = files.sumOf { it.length() }
            val maximum = repository.store.number("cache.megabytes", 512).coerceAtLeast(64) * 1024L * 1024L
            val oldBefore = System.currentTimeMillis() - 60_000
            for(file in files.sortedBy { it.lastModified() }) {
                if(total <= maximum) break
                if(file.lastModified() < oldBefore) { val bytes = file.length(); if(file.delete()) total -= bytes }
            }
        }
    }
    suspend fun aspectRatio(file: File): Float = withContext(Dispatchers.IO) {
        val key = "${file.path}:${file.lastModified()}:${file.length()}"
        ratios.get(key) ?: BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.path, this)
            (if(outWidth > 0 && outHeight > 0) outWidth.toFloat() / outHeight else .7f).also { ratios.put(key, it) }
        }
    }
    private fun restoreJm(bitmap: Bitmap, url: String): Bitmap {
        val match = Regex("/photos/(\\d+)/([^/.]+)").find(url) ?: return bitmap
        val id = match.groupValues[1].toIntOrNull() ?: return bitmap
        val name = match.groupValues[2]
        val count = when {
            id < 220980 -> 0
            id < 268850 -> 10
            else -> {
                val hash = MessageDigest.getInstance("MD5").digest("$id$name".toByteArray()).joinToString("") { "%02x".format(it) }
                hash.last().code % (if(id > 421926) 8 else 10) * 2 + 2
            }
        }
        if(count == 0 || bitmap.height < count) return bitmap
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val height = bitmap.height / count
        val remainder = bitmap.height % count
        val paint = Paint()
        for(i in 0 until count) {
            val sourceY = bitmap.height - height * (i + 1) - remainder
            val targetY = if(i == 0) 0 else height * i + remainder
            val stripHeight = height + if(i == 0) remainder else 0
            canvas.drawBitmap(bitmap, Rect(0, sourceY, bitmap.width, sourceY + stripHeight), Rect(0, targetY, bitmap.width, targetY + stripHeight), paint)
        }
        return output
    }
}
