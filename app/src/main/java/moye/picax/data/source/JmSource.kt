package moye.picax.data.source

import android.util.Base64
import kotlinx.coroutines.CancellationException
import moye.picax.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class JmSource(private val http: SourceHttp) : SourceClient {
    override val platform = Platform.jmComic
    private val imageBase get() = SourceSettings.jmImageBase(http.store)
    private fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private suspend fun api(path: String, body: String? = null): Any {
        val bases = SourceSettings.jmRequestBases(http.store)
        var failure: Exception? = null
        for (base in bases) try {
            val time = System.currentTimeMillis() / 1000
            val headers = headers(time)
            val response = http.response(base.trimEnd('/') + "/" + path, headers, body, contentType = "application/x-www-form-urlencoded")
            val payload = response.use {
                val cookies = it.headers.values("Set-Cookie").map { value -> value.substringBefore(';') }
                if (cookies.isNotEmpty()) {
                    val old = http.store.vault.get(platform)
                    val combined = (old.cookie.split("; ") + cookies).filter { value -> value.contains('=') }.associate { value -> value.substringBefore('=') to value.substringAfter('=') }
                    http.store.vault.save(platform, old.copy(cookie = combined.map { pair -> "${pair.key}=${pair.value}" }.joinToString("; ")))
                }
                JSONObject(it.body!!.string())
            }
            require(payload.optInt("code", 200) == 200) { payload.optString("errorMsg", "JMComic 请求失败") }
            val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(md5("${time}185Hcomic3PAPP7R").toByteArray(), "AES")) }
            val decoded = String(cipher.doFinal(Base64.decode(payload.getString("data"), Base64.DEFAULT)))
            val text = decoded.substring(0, maxOf(decoded.lastIndexOf('}'), decoded.lastIndexOf(']')) + 1)
            return if (text.startsWith('[')) JSONArray(text) else JSONObject(text)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e }
        throw failure ?: IllegalArgumentException("请配置 JMComic API 地址")
    }
    internal fun headers(time: Long = System.currentTimeMillis() / 1000) = http.headers(platform) + mapOf("token" to md5("${time}18comicAPPContent"), "tokenparam" to "$time,${http.store.setting("jm.version", "2.1.4")}", "Referer" to "https://localhost/", "Origin" to "https://localhost", "Authorization" to "Bearer")
    private fun items(value: Any): List<Comic> {
        val rows = when(value) { is JSONArray -> value; is JSONObject -> value.optJSONArray("content") ?: value.optJSONArray("list"); else -> null }
        return rows.objects().map { doc ->
            val id = doc.getString("id")
            Comic(id, platform, doc.optString("name", id), doc.optString("author"), "$imageBase/media/albums/${id}_3x4.jpg",
                listOfNotNull(doc.optJSONObject("category")?.optString("title"), doc.optJSONObject("category_sub")?.optString("title")))
        }
    }
    override suspend fun search(query: String, page: Int, sort: String) = items(api("search?&search_query=${query.encoded()}&o=${if(sort in listOf("mr","mv","mp","tf")) sort else "mr"}&page=$page"))
    override suspend fun explore(kind: String, page: Int): List<Comic> = items(api(when {
        kind.startsWith("category:") -> "categories/filter?c=${kind.substringAfter(':').encoded()}&o=mr&page=$page"
        kind.startsWith("popular") -> "categories/filter?c=0&o=${if(kind == "popular-month") "mv_m" else "mv"}&t=${when(kind) { "popular-today" -> "t"; "popular-week" -> "w"; else -> "a" }}&page=$page"
        else -> "latest?page=$page"
    })).let { if (kind == "random") it.shuffled() else it }
    override suspend fun detail(comic: Comic): ComicDetail {
        val doc = api("album?id=${comic.id}") as JSONObject
        val tags = listOf("author", "tags", "works", "actors").flatMap { doc.optJSONArray(it).strings() }
        val chapters = doc.optJSONArray("series").objects().mapIndexed { index, item -> Chapter(item.getString("id"), item.optString("name").ifBlank { "第 ${index + 1} 话" }, item.optInt("sort", index + 1)) }.sortedBy { it.order }
        val groups = listOf("author" to "作者", "tags" to "标签", "works" to "作品", "actors" to "角色").map { (key, title) -> TagGroup(title, doc.optJSONArray(key).strings()) }.filter { it.tags.isNotEmpty() }
        return ComicDetail(comic.copy(title = doc.optString("name", comic.title), subtitle = doc.optJSONArray("author").strings().firstOrNull().orEmpty(), tags = tags, likes = doc.optInt("likes")), doc.optString("description"),
            chapters.ifEmpty { listOf(Chapter(comic.id, "第 1 话")) }, items(doc.optJSONArray("related_list") ?: JSONArray()),
            "阅读 ${doc.optInt("total_views")} · 喜欢 ${doc.optInt("likes")} · 评论 ${doc.optInt("comment_total")}", groups, isLiked = doc.optBoolean("liked"))
    }
    override suspend fun pages(comic: Comic, chapter: Chapter) = (api("chapter?&id=${chapter.id}") as JSONObject).optJSONArray("images").strings().map { "$imageBase/media/photos/${chapter.id}/$it" }
    override suspend fun categories() = listOf("最新A漫", "同人", "單本", "短篇", "韓漫", "美漫", "Cosplay", "3D", "禁漫漢化組", "全彩", "纯爱", "人妻", "NTR", "百合", "教师", "御姐", "巨乳").map { Category(it) }
    override suspend fun comments(comic: Comic, page: Int) = (api("forum?mode=manhua&aid=${comic.id}&page=$page") as JSONObject).optJSONArray("list").objects()
        .map { Comment(it.optString("CID"), it.optString("username"), Jsoup.parse(it.optString("content")).text(), it.optString("addtime")) }
    override suspend fun favorites(page: Int) = favorites(page, "0")
    override suspend fun favorites(page: Int, folder: String) = items(api("favorite?page=$page&folder_id=${folder.encoded()}&o=${SourceSettings.jmFavoriteSort(http.store.setting("jm.favoriteSort", "latest"))}"))
    override suspend fun favoriteFolders(): List<FavoriteFolder> = listOf(FavoriteFolder("0", "全部收藏")) +
        (api("favorite") as JSONObject).optJSONArray("folder_list").objects().map { FavoriteFolder(it.optString("FID"), it.optString("name")) }
    override suspend fun postComment(comic: Comic, content: String) {
        require(http.store.vault.get(platform).cookie.isNotBlank()) { "请先登录 JMComic" }
        api("comment", "comment=${content.encoded()}&status=undefined&aid=${comic.id.encoded()}")
    }
    override suspend fun addFavorite(comic: Comic) = addFavorite(comic, "0")
    override suspend fun addFavorite(comic: Comic, folder: String) {
        val result = api("favorite", "aid=${comic.id.encoded()}") as? JSONObject
        if(result?.optString("type") != "add") api("favorite", "aid=${comic.id.encoded()}")
        if(folder != "0") api("favorite_folder", "type=move&folder_id=${folder.encoded()}&aid=${comic.id.encoded()}")
    }
    override suspend fun like(comic: Comic) { api("like", "id=${comic.id.encoded()}") }
    override suspend fun postChapterComment(comic: Comic, chapter: Chapter?, content: String) = postComment(if(chapter == null) comic else comic.copy(id = chapter.id), content)
    override suspend fun chapterComments(comic: Comic, chapter: Chapter, page: Int) = comments(comic.copy(id = chapter.id), page)
    override suspend fun checkIn(): String {
        val user = api("login", "username=${http.store.data.value.accounts.firstOrNull { it.platform == platform }?.username.orEmpty().encoded()}&password=${http.store.vault.get(platform).password.encoded()}") as JSONObject
        val id = user.optString("uid", user.optString("user_id", user.optString("id")))
        require(id.isNotBlank()) { "签到前请重新登录" }
        val daily = api("daily?user_id=${id.encoded()}") as JSONObject
        val result = api("daily_chk", "user_id=${id.encoded()}&daily_id=${daily.optString("daily_id").encoded()}&")
        return (result as? JSONObject)?.optString("msg")?.ifBlank { "签到完成" } ?: "签到完成"
    }
    override suspend fun login(username: String, password: String): Credential {
        val data = api("login", "username=${username.encoded()}&password=${password.encoded()}") as JSONObject
        require(data.optString("username").isNotEmpty()) { "JMComic 登录失败" }
        return http.store.vault.get(platform).copy(password = password)
    }
}
