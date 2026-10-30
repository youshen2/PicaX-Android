package moye.picax.data.source

import moye.picax.model.*
import org.json.JSONObject
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PicacgSource(private val http: SourceHttp) : SourceClient {
    override val platform = Platform.picacg
    internal fun headers(path: String, method: String, channel: String = http.store.setting("picacg.channel", "3")): Map<String, String> {
        val apiKey = "C69BAF41DA5ABD1FFEDC6D2FEA56B"
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val time = (System.currentTimeMillis() / 1000).toString()
        val secret = "~d}\$Q7\$eIni=V)9\\RK/P.RM4;9[7|@/CA}b~OW!3?EV`:<>M7pddUBL5n|0/*Cn"
        val signature = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            doFinal((path + time + nonce + method + apiKey).lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
        }
        return mapOf("api-key" to apiKey, "accept" to "application/vnd.picacomic.com.v1+json", "app-channel" to channel,
            "authorization" to http.store.vault.get(platform).token, "time" to time, "nonce" to nonce, "signature" to signature,
            "app-version" to "2.2.1.3.3.4", "app-uuid" to "defaultUuid", "image-quality" to http.store.setting("picacg.quality", "medium"),
            "app-platform" to "android", "app-build-version" to "45", "User-Agent" to "okhttp/3.8.1", "version" to "v1.4.1")
    }
    private suspend fun api(path: String, body: JSONObject? = null): JSONObject {
        require(body != null && path == "auth/sign-in" || http.store.vault.get(platform).token.isNotEmpty()) { "请先在设置中登录 PicACG 账号" }
        return http.json(http.base(platform) + "/" + path, platform, headers(path, if (body == null) "GET" else "POST"), body?.toString()).getJSONObject("data")
    }
    private fun image(doc: JSONObject?): String = doc?.let { it.optString("fileServer") + "/static/" + it.optString("path") }.orEmpty()
    private fun item(doc: JSONObject) = Comic(doc.getString("_id"), platform, doc.optString("title"), doc.optString("author"), image(doc.optJSONObject("thumb")),
        (doc.optJSONArray("tags").strings() + doc.optJSONArray("categories").strings()).distinct(), doc.optInt("pagesCount"), doc.optInt("likesCount"))
    override suspend fun search(query: String, page: Int, sort: String): List<Comic> {
        val data = if(query.startsWith("picacg:a:")) api("comics?page=$page&a=${query.substringAfter("picacg:a:").encoded()}&s=dd")
            else if(query.startsWith("picacg:ca:")) api("comics?page=$page&ca=${query.substringAfter("picacg:ca:").encoded()}&s=dd")
            else if (query.startsWith("category:")) api("comics?page=$page&c=${query.removePrefix("category:").encoded()}&s=dd")
            else api("comics/advanced-search?page=$page", JSONObject().put("keyword", query).put("sort", if (sort in listOf("dd", "da", "ld", "vd")) sort else http.store.setting("picacg.sort", "dd")))
        return data.array("comics", "docs").objects().map(::item)
    }
    override suspend fun explore(kind: String, page: Int): List<Comic> = when {
        kind == "random" -> api("comics/random").optJSONArray("comics").objects().map(::item)
        kind.startsWith("popular") -> api("comics/leaderboard?tt=${when(kind) { "popular-today" -> "H24"; "popular-week" -> "D7"; else -> "D30" }}&ct=VC").optJSONArray("comics").objects().map(::item)
        else -> api("comics?page=$page&s=dd").array("comics", "docs").objects().map(::item)
    }
    override suspend fun detail(comic: Comic): ComicDetail {
        val data = api("comics/${comic.id}")
        val chapters = mutableListOf<Chapter>()
        var page = 1
        do {
            val eps = api("comics/${comic.id}/eps?page=$page").getJSONObject("eps")
            chapters += eps.optJSONArray("docs").objects().map { Chapter(it.getString("_id"), it.optString("title"), it.optInt("order")) }
            page++
        } while (page <= eps.optInt("pages", 1))
        val doc = data.getJSONObject("comic")
        val user = doc.optJSONObject("_creator")
        val groups = listOf(
            TagGroup("作者", listOfNotNull(doc.optString("author").takeIf { it.isNotBlank() }?.let { "picacg:a:$it" })),
            TagGroup("汉化", listOfNotNull(doc.optString("chineseTeam").takeIf { it.isNotBlank() })),
            TagGroup("分类", doc.optJSONArray("categories").strings().map { "category:$it" }),
            TagGroup("标签", doc.optJSONArray("tags").strings())
        ).filter { it.tags.isNotEmpty() }
        return ComicDetail(item(doc), doc.optString("description"), chapters.sortedBy { it.order },
            data.optJSONArray("recommendation").objects().map(::item), doc.optString("updated_at"), groups,
            user?.let { Uploader(it.optString("_id"), it.optString("name"), image(it.optJSONObject("avatar")), it.optString("title"), it.optInt("level"), it.optInt("exp"), it.optString("slogan"), it.optString("character")) }, doc.optBoolean("isLiked"))
    }
    override suspend fun pages(comic: Comic, chapter: Chapter): List<String> {
        val result = mutableListOf<String>()
        var page = 1
        do {
            val pages = api("comics/${comic.id}/order/${chapter.order}/pages?page=$page").getJSONObject("pages")
            result += pages.optJSONArray("docs").objects().map { image(it.optJSONObject("media")) }
            page++
        } while (page <= pages.optInt("pages", 1))
        return result
    }
    override suspend fun categories() = api("categories").optJSONArray("categories").objects().filterNot { it.optBoolean("isWeb") }
        .map { Category(it.getString("title"), "category:${it.getString("title")}", image(it.optJSONObject("thumb"))) }
    override suspend fun comments(comic: Comic, page: Int) = api("comics/${comic.id}/comments?page=$page").array("comments", "docs").objects()
        .map { val user = it.optJSONObject("_user"); Comment(it.optString("_id"), user?.optString("name").orEmpty(), it.optString("content"), it.optString("created_at"), image(user?.optJSONObject("avatar")), it.optInt("likesCount"), it.optInt("commentsCount"), user?.optString("character").orEmpty()) }
    override suspend fun favorites(page: Int) = api("users/favourite?s=${http.store.setting("picacg.favoriteSort", "da")}&page=$page").array("comics", "docs").objects().map(::item)
    override suspend fun postComment(comic: Comic, content: String) { api("comics/${comic.id}/comments", JSONObject().put("content", content)) }
    override suspend fun addFavorite(comic: Comic) { api("comics/${comic.id}/favourite", JSONObject()) }
    override suspend fun like(comic: Comic) { api("comics/${comic.id}/like", JSONObject()) }
    override suspend fun checkIn(): String { api("users/punch-in", JSONObject()); return "打卡完成" }
    suspend fun profile(): Uploader {
        val user = api("users/profile").getJSONObject("user")
        return Uploader(user.optString("_id"), user.optString("name"), image(user.optJSONObject("avatar")), user.optString("title"), user.optInt("level"), user.optInt("exp"), user.optString("slogan"), user.optString("character"))
    }
    override suspend fun ownComments(page: Int) = ownCommentPage(page).comments
    override suspend fun ownCommentPage(page: Int): CommentPage {
        val result = api("users/my-comments?page=$page").getJSONObject("comments")
        val rows = result.optJSONArray("docs").objects().map {
            val comic = it.optJSONObject("_comic")?.takeIf { doc -> doc.optString("_id").isNotBlank() }?.let(::item)
            Comment(it.optString("_id"), comic?.title.orEmpty(), it.optString("content"), it.optString("created_at"), likes = it.optInt("likesCount"), replies = it.optInt("commentsCount"), comic = comic)
        }
        return CommentPage(rows, page < result.optInt("pages", page))
    }
    override suspend fun login(username: String, password: String): Credential {
        val data = api("auth/sign-in", JSONObject().put("email", username).put("password", password))
        return Credential(token = data.getString("token"), password = password)
    }
}
