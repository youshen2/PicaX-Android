package moye.picax

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import moye.picax.model.Platform
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class PageImagesCacheTest {
    @Test fun concurrentReadersShareOneFetchAndPreserveBytes() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PicaXApplication
        val payload = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val requests = AtomicInteger()
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true, name = "PicaX image fixture") {
            server.accept().use { connection ->
                requests.incrementAndGet()
                val reader = connection.getInputStream().bufferedReader()
                while(!reader.readLine().isNullOrEmpty()) { }
                connection.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(payload)
                    flush()
                }
            }
        }
        try {
            val url = "http://127.0.0.1:${server.localPort}/${UUID.randomUUID()}.img"
            val files = withTimeout(15_000) { (1..24).map { async(Dispatchers.IO) { app.repository.images.file(url, Platform.nhentai) } }.awaitAll() }
            assertEquals(1, requests.get())
            assertEquals(1, files.map { it.path }.distinct().size)
            assertArrayEquals(payload, files.first().readBytes())
            assertEquals(files.first(), app.repository.images.file(url, Platform.nhentai))
            assertSame(app.repository.http.imageClient(), app.repository.http.imageClient())
            files.first().delete()
        } finally {
            server.close()
            worker.join(1_000)
        }
    }

    @Test fun cachedDimensionsRefreshWhenTheFileChanges() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PicaXApplication
        val file = File(app.cacheDir, "dimensions-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(48, 96, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertEquals(.5f, app.repository.images.aspectRatio(file), .0001f)
            val replacement = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888)
            file.outputStream().use { replacement.compress(Bitmap.CompressFormat.PNG, 100, it) }
            replacement.recycle()
            assertEquals(2f, app.repository.images.aspectRatio(file), .0001f)
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
