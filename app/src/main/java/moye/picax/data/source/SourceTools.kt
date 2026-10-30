package moye.picax.data.source

import android.util.Base64
import kotlinx.coroutines.*
import moye.picax.data.ComicRepository
import moye.picax.model.Platform
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class SourceRoute(val id: String, val title: String, val url: String)
data class RouteTiming(val route: SourceRoute, val milliseconds: Long? = null, val error: String? = null)
data class EhProfile(val id: String, val title: String)

class SourceTools(private val repository: ComicRepository) {
    private val http get() = repository.http
    private val store get() = repository.store
    fun jmRoutes() = SourceSettings.jmCandidates(store).mapIndexed { i, url -> SourceRoute(i.toString(), "分流 ${i + 1}", url) }
    fun imageRoutes() = (SourceSettings.jmImages + SourceSettings.baseURL(store.setting("jm.images"), SourceSettings.jmImages[1]))
        .mapIndexed { i, url -> SourceRoute(i.toString(), if (i == 4) "自定义" else "分流 ${i + 1}", url) }
    val ehRoutes get() = listOf("https://e-hentai.org", "https://exhentai.org").map { SourceRoute(it, it.substringAfter("://"), it) }
    val picacgRoutes get() = (1..3).map { SourceRoute(it.toString(), "分流 $it", http.base(Platform.picacg)) }

    suspend fun test(platform: Platform, routes: List<SourceRoute>, images: Boolean = false): List<RouteTiming> = coroutineScope {
        routes.map { route -> async(Dispatchers.IO) {
            val started = System.nanoTime()
            try {
                val path = when {
                    images -> ""
                    platform == Platform.picacg -> "/categories"
                    platform == Platform.jmComic -> "/latest?page=1"
                    platform == Platform.htManga -> "/albums.html"
                    else -> "/"
                }
                val headers = when (platform) {
                    Platform.picacg -> (repository.source(platform) as PicacgSource).headers("categories", "GET", route.id)
                    Platform.jmComic -> if (images) http.headers(platform) else (repository.source(platform) as JmSource).headers()
                    else -> http.headers(platform)
                }
                val request = Request.Builder().url(route.url + path).header("Cache-Control", "no-cache")
                    .apply { headers.forEach { (key, value) -> header(key, value) }; if (images) header("Range", "bytes=0-65535") }.build()
                http.execute(request, timeoutSeconds = 12, acceptsClientError = images).use { response ->
                    if (platform == Platform.picacg) require(JSONObject(response.body!!.string()).optInt("code", 200) == 200) { "失败" }
                }
                RouteTiming(route, ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { RouteTiming(route, error = if (e is java.io.InterruptedIOException) "超时" else "失败") }
        } }.awaitAll()
    }

    suspend fun ehProfiles(): List<EhProfile> = withContext(Dispatchers.IO) {
        val html = http.text(http.base(Platform.eHentai) + "/uconfig.php", Platform.eHentai)
        val options = Jsoup.parse(html).select("select[name=profile_set] option")
        require(options.isNotEmpty()) { "E-Hentai 没有返回 Profile 列表，请检查登录状态" }
        listOf(EhProfile("", "Do not modify")) + options.filter { it.attr("value").isNotEmpty() }.map { EhProfile(it.attr("value"), it.text()) }
    }

    suspend fun htRoutes(): List<SourceRoute> = withContext(Dispatchers.IO) {
        val text = http.text("https://raw.githubusercontent.com/ccbkv/PicaComicapitxt/refs/heads/main/htmanga_api_list.txt", Platform.local)
        val urls = text.lineSequence().map(String::trim).filter(String::isNotEmpty).map { line ->
            val value = if (line.startsWith("http")) line else runCatching { String(Base64.decode(line, Base64.DEFAULT)) }.getOrDefault(line)
            SourceSettings.baseURL(value, "")
        }.filter { runCatching { java.net.URI(it).host != null }.getOrDefault(false) }.distinct().toList()
        require(urls.isNotEmpty()) { "绅士漫画没有返回可用 API 分流" }
        urls.map { SourceRoute(it, it.substringAfter("://"), it) }
    }

    suspend fun updateJmDomains(): List<String> = withContext(Dispatchers.IO) {
        var failure: Exception? = null
        for (url in listOf("https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt", "https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt")) {
            try {
                val encrypted = http.text(url, Platform.local).trim()
                val key = MessageDigest.getInstance("MD5").digest("diosfjckwpqpdfjkvnqQjsik".toByteArray()).joinToString("") { "%02x".format(it) }
                val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(), "AES")) }
                val decoded = String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)))
                val urls = JSONObject(decoded.take(decoded.lastIndexOf('}') + 1)).getJSONArray("Server").strings()
                    .map { SourceSettings.baseURL(it, "") }.filter { java.net.URI(it).host != null }.distinct()
                require(urls.isNotEmpty()) { "JMComic 域名响应没有有效 Server" }
                store.set("jm.apis", urls.joinToString("\n"))
                try { updateJmVersion() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                return@withContext urls
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e }
        }
        throw failure ?: IllegalStateException("JMComic API 域名更新失败")
    }

    suspend fun updateJmVersion(): String {
        var failure: Exception? = null
        for (base in (SourceSettings.jmCandidates(store) + SourceSettings.jmAPIs).distinct()) {
            try {
                val version = http.json("$base/static/jmapp3apk/version.json", Platform.local).getString("version").trim()
                require(version.isNotBlank()) { "JMComic App 版本响应缺少 version" }
                store.set("jm.version", version)
                return version
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e }
        }
        throw failure ?: IllegalStateException("JMComic App 版本更新失败")
    }
}
