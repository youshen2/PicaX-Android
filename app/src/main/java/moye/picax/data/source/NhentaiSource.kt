package moye.picax.data.source

import moye.picax.model.*
import org.json.JSONObject

class NhentaiSource(private val http: SourceHttp) : SourceClient {
    override val platform = Platform.nhentai
    private fun url(path: String) = http.base(platform) + "/api/v2/" + path
    private fun absolute(path: String, image: Boolean = false) = if (path.startsWith("http")) path else "https://${if (image) "i" else "t"}.nhentai.net/" + path.trimStart('/')
    private fun item(doc: JSONObject): Comic {
        val title = doc.optJSONObject("title")
        val tags = doc.optJSONArray("tags").objects()
        val cover = doc.optJSONObject("cover")?.optString("path") ?: doc.optString("thumbnail")
        return Comic(doc.optString("id"), platform, title?.optString("english")?.ifEmpty { title.optString("japanese") }
            ?: doc.optString("english_title", doc.optString("japanese_title")), doc.optString("japanese_title"), absolute(cover),
            tags.map { "${it.optString("type")}:${it.optString("name")}" }, doc.optInt("num_pages"), doc.optInt("num_favorites"),
            tags.firstOrNull { it.optString("type") == "language" }?.optString("name").orEmpty())
    }
    override suspend fun search(query: String, page: Int, sort: String) =
        http.json(url("search?query=${query.ifBlank { " " }.encoded()}&page=$page&sort=${sort.encoded()}"), platform).optJSONArray("result").objects().map(::item)
    override suspend fun explore(kind: String, page: Int) = search(" ", page, if (kind == "latest") "date" else kind)
    override suspend fun detail(comic: Comic): ComicDetail {
        val json = http.json(url("galleries/${comic.id}"), platform)
        val book = item(json)
        val date = json.optLong("upload_date").takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }.orEmpty()
        return ComicDetail(book, json.optJSONObject("title")?.optString("japanese").orEmpty(), updatedText = date, tagGroups = groupedTags(book.tags))
    }
    override suspend fun pages(comic: Comic, chapter: Chapter) = http.json(url("galleries/${comic.id}"), platform)
        .optJSONArray("pages").objects().map { absolute(it.getString("path"), true) }
    override suspend fun categories() = moye.picax.data.TagCatalog(http.store.context).nhentaiCategories()
    override suspend fun comments(comic: Comic, page: Int) = http.json(url("galleries/${comic.id}/comments?page=$page"), platform)
        .optJSONArray("result").objects().map { Comment(it.optString("id"), it.optJSONObject("poster")?.optString("username").orEmpty(), it.optString("body"), it.optString("post_date")) }
    override suspend fun favorites(page: Int) = http.json(url("favorites?page=$page"), platform).optJSONArray("result").objects().map(::item)
    override suspend fun addFavorite(comic: Comic) { http.response(url("galleries/${comic.id}/favorite"), http.headers(platform), "{}").close() }
}
