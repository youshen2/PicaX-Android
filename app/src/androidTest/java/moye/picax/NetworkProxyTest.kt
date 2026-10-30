package moye.picax

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moye.picax.model.Platform
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class NetworkProxyTest {
    @Test fun configuredExternalProxyForwardsRequests() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PicaXApplication
        val preferences = app.store.data.value.preferences
        val requestLine = AtomicReference<String>()
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            server.accept().use { connection ->
                val reader = connection.getInputStream().bufferedReader()
                requestLine.set(reader.readLine())
                while(!reader.readLine().isNullOrEmpty()) { }
                connection.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 8\r\nConnection: close\r\n\r\nproxy-ok".toByteArray())
                    flush()
                }
            }
        }
        try {
            app.store.update { it.copy(preferences = it.preferences + mapOf(
                "network.mode" to "proxy", "network.type" to "HTTP",
                "network.host" to "127.0.0.1", "network.port" to server.localPort.toString(), "network.username" to ""
            )) }
            assertEquals("proxy-ok", app.repository.http.text("http://example.invalid/fixture", Platform.local))
            assertEquals("GET http://example.invalid/fixture HTTP/1.1", requestLine.get())
            app.store.set("network.type", "SOCKS")
            assertEquals(Proxy.Type.SOCKS, app.repository.http.client().proxy!!.type())
            app.store.set("network.mode", "direct")
            assertNull(app.repository.http.client().proxy)
        } finally {
            app.store.update { it.copy(preferences = preferences) }
            server.close(); worker.join(1000)
        }
    }
}
