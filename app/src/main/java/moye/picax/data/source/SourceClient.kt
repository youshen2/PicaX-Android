package moye.picax.data.source

import kotlinx.coroutines.suspendCancellableCoroutine
import moye.picax.data.AppStore
import moye.picax.model.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface SourceClient {
    val platform: Platform
    suspend fun search(query: String, page: Int, sort: String): List<Comic>
    suspend fun explore(kind: String, page: Int): List<Comic> = search("", page, kind)
    suspend fun detail(comic: Comic): ComicDetail
    suspend fun pages(comic: Comic, chapter: Chapter): List<String>
    suspend fun categories(): List<Category>
    suspend fun comments(comic: Comic, page: Int): List<Comment> = emptyList()
    suspend fun postComment(comic: Comic, content: String) { error("该来源未提供发送评论接口") }
    suspend fun postChapterComment(comic: Comic, chapter: Chapter?, content: String) = postComment(comic, content)
    suspend fun favorites(page: Int): List<Comic> = emptyList()
    suspend fun favoriteFolders(): List<FavoriteFolder> = listOf(FavoriteFolder("0", "全部收藏"))
    suspend fun favorites(page: Int, folder: String): List<Comic> = favorites(page)
    suspend fun addFavorite(comic: Comic, folder: String) = addFavorite(comic)
    suspend fun checkIn(): String { error("该来源没有签到功能") }
    suspend fun ownComments(page: Int): List<Comment> = emptyList()
    suspend fun ownCommentPage(page: Int): CommentPage = ownComments(page).let { CommentPage(it, it.isNotEmpty()) }
    suspend fun chapterComments(comic: Comic, chapter: Chapter, page: Int): List<Comment> = comments(comic, page)
    suspend fun like(comic: Comic) { error("该来源没有点赞接口") }
    suspend fun addFavorite(comic: Comic) { error("该来源未提供云端收藏接口") }
    suspend fun login(username: String, password: String): Credential { error("请使用网页登录或保存 Cookie") }
}

class SourceHttp(val store: AppStore) {
    private val direct = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .cache(Cache(java.io.File(store.context.cacheDir, "http"), 64L * 1024 * 1024)).build()
    @Volatile private var lastProxy = ""
    @Volatile private var proxyClient: OkHttpClient? = null
    private var imageParent: OkHttpClient? = null
    private var images: OkHttpClient? = null
    @Synchronized fun imageClient(): OkHttpClient {
        val parent = client()
        if(imageParent !== parent) {
            imageParent = parent
            images = parent.newBuilder().addInterceptor { chain ->
                val host = chain.request().url.host
                val platform = when {
                    "nhentai" in host -> Platform.nhentai
                    "jmapiproxy" in host -> Platform.jmComic
                    "hitomi" in host || "gold-usergeneratedcontent" in host -> Platform.hitomi
                    "ehgt" in host || "e-hentai" in host -> Platform.eHentai
                    "picacomic" in host -> Platform.picacg
                    "wnacg" in host -> Platform.htManga
                    else -> null
                }
                val request = chain.request().newBuilder()
                if(platform != null) imageHeaders(platform, chain.request().url.toString()).forEach { (key, value) -> request.header(key, value) }
                chain.proceed(request.build())
            }.build()
        }
        return requireNotNull(images)
    }
    fun client(): OkHttpClient {
        if (store.setting("network.mode", "direct") != "proxy") return direct
        val host = store.setting("network.host", "127.0.0.1").trim()
        val port = store.number("network.port", 7890)
        require(host.isNotEmpty() && port in 1..65535) { "请在网络与代理中填写有效的服务器地址和端口" }
        val key = "$host:$port:${store.setting("network.type", "HTTP")}:${store.setting("network.username")}:${store.vault.get(Platform.local).password}"
        if (key != lastProxy || proxyClient == null) synchronized(this) {
            val type = if (store.setting("network.type") == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
            proxyClient = direct.newBuilder().proxy(Proxy(type, InetSocketAddress(host, port))).proxyAuthenticator { _, response ->
                if (response.request.header("Proxy-Authorization") != null) null else response.request.newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(store.setting("network.username"), store.vault.get(Platform.local).password)).build()
            }.build()
            lastProxy = key
        }
        return proxyClient!!
    }
    fun headers(platform: Platform): Map<String, String> {
        val cred = store.vault.get(platform)
        return buildMap {
            put("User-Agent", cred.userAgent.ifEmpty { "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/138.0.0.0 Mobile Safari/537.36" })
            put("Referer", base(platform) + "/")
            val cookies = buildList {
                if(cred.cookie.isNotBlank()) add(cred.cookie)
                if(platform == Platform.eHentai) {
                    if(store.bool("eh.ignoreWarning", true)) add("nw=1")
                    val profile = store.setting("eh.profile"); if(profile.isNotBlank()) add("sp=$profile")
                }
            }.joinToString("; ")
            if(cookies.isNotBlank()) put("Cookie", cookies)
            if (platform == Platform.nhentai && cred.token.isNotBlank()) put("Authorization", "User ${cred.token}")
        }
    }
    fun base(platform: Platform) = SourceSettings.baseURL(
        if (platform == Platform.picacg) store.setting("source.${platform.name}.url", platform.website)
        else store.setting("frontend.${platform.name}", store.setting("source.${platform.name}.url", platform.website)), platform.website)
    fun imageHeaders(platform: Platform, url: String): Map<String, String> {
        val host = java.net.URI(url).host.orEmpty()
        val origin = java.net.URI(base(platform)).host.orEmpty()
        val sameSite = origin.isNotEmpty() && (host == origin || host.endsWith(".$origin"))
        return headers(platform).filterKeys { sameSite || (!it.equals("Cookie", true) && !it.equals("Authorization", true)) }
    }
    suspend fun response(url: String, headers: Map<String, String> = emptyMap(), body: String? = null, method: String = if (body == null) "GET" else "POST", contentType: String = "application/json"): Response {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
            method(method, body?.toRequestBody(contentType.toMediaType()))
        }.build()
        return execute(request)
    }
    suspend fun execute(request: Request, timeoutSeconds: Long? = null, acceptsClientError: Boolean = false): Response {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { suspendCancellableCoroutine { continuation ->
            val client = client()
            val call = (if (timeoutSeconds == null) client else client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()).newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful && !(acceptsClientError && response.code in 400..499)) {
                        response.close()
                        if (!continuation.isCancelled) continuation.resumeWithException(IOException(if (response.code == 401) "登录已过期，请重新登录" else "请求失败（HTTP ${response.code}）"))
                    } else continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        } }
    }
    suspend fun text(url: String, platform: Platform, headers: Map<String, String> = emptyMap(), body: String? = null, contentType: String = "application/json") =
        response(url, this.headers(platform) + headers, body, contentType = contentType).use { it.body!!.string() }
    suspend fun json(url: String, platform: Platform, headers: Map<String, String> = emptyMap(), body: String? = null) = JSONObject(text(url, platform, headers, body))
}

internal fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")
internal fun org.json.JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
internal fun org.json.JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
internal fun JSONObject.array(vararg keys: String): org.json.JSONArray? {
    var current = this
    for (key in keys.dropLast(1)) current = current.optJSONObject(key) ?: return null
    return current.optJSONArray(keys.last())
}
