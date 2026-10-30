package moye.picax

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moye.picax.data.source.*
import moye.picax.model.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class PlatformSourceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PicaXApplication

    private fun serve(body: String, cookie: String? = null, test: (String, AtomicReference<String>) -> Unit) {
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val received = AtomicReference<String>()
        val worker = thread(isDaemon = true) {
            server.accept().use { connection ->
                val reader = connection.getInputStream().bufferedReader()
                val header = buildList { while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; add(line) } }
                val length = header.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                val content = CharArray(length)
                var read = 0
                while (read < length) { val n = reader.read(content, read, length - read); if (n < 0) break; read += n }
                received.set(header.joinToString("\n") + "\n\n" + String(content))
                val payload = body.toByteArray()
                val cookies = cookie?.let { "Set-Cookie: $it\r\n" }.orEmpty()
                connection.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\n${cookies}Connection: close\r\n\r\n").toByteArray() + payload)
            }
        }
        try { test("http://127.0.0.1:${server.localPort}", received) }
        finally { server.close(); worker.join(1000) }
    }

    @Test fun htPasswordLoginPostsCredentialsAndRetainsSession() {
        val original = app.store.data.value
        try {
            serve("登录成功", "session=fixture; Path=/; HttpOnly") { base, received -> runBlocking {
                app.store.set("network.mode", "direct"); app.store.set("frontend.htManga", base)
                val credential = app.repository.source(Platform.htManga).login("user name", "p&ss")
                assertTrue(received.get().startsWith("POST /users-check_login.html"))
                assertTrue(received.get().contains("login_name=user+name&login_pass=p%26ss"))
                assertEquals("session=fixture", credential.cookie)
                assertEquals("p&ss", credential.password)
            } }
        } finally { app.store.update { original } }
    }

    @Test fun picacgOwnCommentsPreserveComicAndPagination() {
        val original = app.store.data.value
        val credential = app.store.vault.get(Platform.picacg)
        try {
            serve("""{"code":200,"data":{"comments":{"pages":2,"docs":[{"_id":"comment","content":"content","likesCount":4,"commentsCount":3,"_comic":{"_id":"comic","title":"Comic title"}}]}}}""") { base, received -> runBlocking {
                app.store.set("network.mode", "direct"); app.store.set("source.picacg.url", base)
                app.store.vault.save(Platform.picacg, Credential(token = "fixture"))
                val page = app.repository.source(Platform.picacg).ownCommentPage(1)
                assertTrue(page.hasMore)
                assertEquals("comic", page.comments.single().comic?.id)
                assertEquals(4, page.comments.single().likes)
                assertTrue(received.get().contains("app-channel: 3", true))
            } }
        } finally { app.store.update { original }; app.store.vault.save(Platform.picacg, credential) }
    }

    @Test fun ehProfileOptionsAreLoadedFromConfiguredSite() {
        val original = app.store.data.value
        try {
            serve("""<select name="profile_set"><option value="">Do not modify</option><option value="42">Reading</option></select>""") { base, received -> runBlocking {
                app.store.set("network.mode", "direct"); app.store.set("frontend.eHentai", base)
                val profiles = SourceTools(app.repository).ehProfiles()
                assertEquals(listOf("", "42"), profiles.map { it.id })
                assertEquals("Reading", profiles[1].title)
                assertTrue(received.get().startsWith("GET /uconfig.php"))
            } }
        } finally { app.store.update { original } }
    }
}
