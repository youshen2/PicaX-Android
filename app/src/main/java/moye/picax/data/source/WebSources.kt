package moye.picax.data.source

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moye.picax.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class WebSource(override val platform: Platform, private val http: SourceHttp) : SourceClient {
    private val base get() = http.base(platform)
    override suspend fun login(username: String, password: String): Credential {
        require(platform == Platform.htManga) { "请通过网页登录" }
        return http.response("$base/users-check_login.html", http.headers(platform), "login_name=${username.encoded()}&login_pass=${password.encoded()}", contentType = "application/x-www-form-urlencoded; charset=UTF-8").use { response ->
            val text = response.body!!.string()
            require("登錄成功" in text || "登录成功" in text) { "绅士漫画登录失败" }
            val cookies = generateSequence(response) { it.priorResponse }.flatMap { it.headers.values("Set-Cookie").asSequence() }
                .map { it.substringBefore(';') }.filter { '=' in it }.toList().asReversed().distinctBy { it.substringBefore('=') }.joinToString("; ")
            require(cookies.isNotBlank()) { "绅士漫画没有返回登录 Cookie" }
            Credential(cookie = cookies, password = password, baseUrl = base)
        }
    }
    private suspend fun doc(path: String): Document = Jsoup.parse(http.text(if (path.startsWith("http")) path else base + path, platform), base)
    private fun htItems(document: Document): List<Comic> = document.select("li, .asTB").mapNotNull { row ->
        val link = row.selectFirst("a[href*=aid-]") ?: return@mapNotNull null
        val id = Regex("aid-(\\d+)").find(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
        val img = row.selectFirst("img")
        val title = row.selectFirst(".title, .title a, .pic_box a, .l_title a")?.attr("title")?.ifBlank { null }
            ?: row.selectFirst("a[title]")?.attr("title") ?: row.selectFirst(".title, .l_title")?.text() ?: id
        Comic(id, platform, title, cover = img?.absUrl("src").orEmpty())
    }.distinctBy { it.key }
    private fun ehItems(document: Document): List<Comic> = document.select("tr, .gl1t").mapNotNull { row ->
        val link = row.selectFirst("a[href*=/g/]") ?: return@mapNotNull null
        val title = row.selectFirst(".glink")?.text() ?: return@mapNotNull null
        val img = row.selectFirst("img")
        val cover = img?.absUrl("data-src")?.ifEmpty { img.absUrl("src") }.orEmpty()
        Comic(link.absUrl("href"), platform, title, row.selectFirst(".glname")?.text().orEmpty(), cover,
            row.select("div[title]").map { it.attr("title") }.filter { ':' in it })
    }.distinctBy { it.key }
    override suspend fun search(query: String, page: Int, sort: String): List<Comic> = if (platform == Platform.eHentai)
        ehItems(doc("/?f_search=${query.encoded()}&page=${page - 1}"))
        else htItems(doc("/search/?q=${query.encoded()}&f=_all&s=create_time_DESC&syn=yes&p=$page"))
    override suspend fun explore(kind: String, page: Int): List<Comic> = if (platform == Platform.eHentai)
        ehItems(doc(if (kind.startsWith("popular")) "/popular" else "/?page=${page - 1}"))
        else htItems(doc(if (kind.startsWith("popular")) "/albums-favorite_ranking${if(page > 1) "-page-$page" else ""}-type-${when(kind) { "popular-today" -> "day"; "popular-week" -> "week"; else -> "month" }}.html" else "/albums-index-page-$page.html"))
    override suspend fun detail(comic: Comic): ComicDetail {
        val document = doc(if (platform == Platform.eHentai) comic.id else "/photos-index-page-1-aid-${comic.id}.html")
        val tags = if (platform == Platform.eHentai) document.select("#taglist a").map { it.text() } else document.select(".tagshow a, .tagbtn").map { it.text() }
        val title = document.selectFirst(if(platform == Platform.eHentai) (if(http.store.bool("eh.japaneseTitle") && document.selectFirst("#gj")?.text()?.isNotBlank() == true) "#gj" else "#gn") else ".userwrap h2")?.text().orEmpty().ifEmpty { comic.title }
        val cover = document.selectFirst(if(platform == Platform.eHentai) "#gd1 div" else ".uwthumb img")?.let {
            if (platform == Platform.eHentai) Regex("url\\(['\"]?(.*?)['\"]?\\)").find(it.attr("style"))?.groupValues?.get(1).orEmpty() else it.absUrl("src")
        }.orEmpty().ifEmpty { comic.cover }
        val count = Regex("(\\d+) (?:pages|頁|页|張|张)").find(document.text())?.groupValues?.get(1)?.toIntOrNull() ?: comic.pages
        val groups = if(platform == Platform.eHentai) document.select("#taglist tr").map { row ->
            val namespace = row.selectFirst(".tc")?.text().orEmpty().trimEnd(':')
            TagGroup(namespace, row.select("a").map { "$namespace:${it.text()}" })
        }.filter { it.tags.isNotEmpty() } else groupedTags(tags)
        val updated = if(platform == Platform.eHentai) document.select("#gdd tr").firstOrNull { it.text().startsWith("Posted") }?.select("td")?.last()?.text().orEmpty() else ""
        val uploader = document.selectFirst(if(platform == Platform.eHentai) "#gdn a" else ".uwuinfo p")
        return ComicDetail(comic.copy(title = title, cover = cover, tags = groups.flatMap { it.tags }, pages = count), document.selectFirst(".uwconn, #gj")?.text().orEmpty(), updatedText = updated,
            tagGroups = groups, uploader = uploader?.let { Uploader(it.attr("href"), it.text()) })
    }
    override suspend fun pages(comic: Comic, chapter: Chapter): List<String> {
        if (platform == Platform.htManga) {
            val html = http.text("$base/photos-gallery-aid-${comic.id}.html", platform)
            return Regex("//[^\"\\\\\\s,}]+?\\.(?:jpe?g|png|webp|gif|avif)", RegexOption.IGNORE_CASE).findAll(html).map { "https:" + it.value }.distinct().toList()
        }
        val initial = doc(comic.id)
        val galleryPages = (listOf(comic.id) + initial.select(".ptt a[href]").map { it.absUrl("href") }).distinct()
        val links = mutableListOf<String>()
        for (url in galleryPages) links += (if(url == comic.id) initial else doc(url)).select(".gdtm a, .gdtl a, #gdt a").map { it.absUrl("href") }
        return links.distinct()
    }
    override suspend fun categories(): List<Category> = if (platform == Platform.eHentai)
        moye.picax.data.TagCatalog(http.store.context).ehentaiCategories()
        else listOf("Cosplay", "3D", "同人", "單行本", "短篇", "全彩").map { Category(it) }
    override suspend fun comments(comic: Comic, page: Int): List<Comment> {
        if (platform != Platform.eHentai) return emptyList()
        return doc(comic.id).select(".c1").mapIndexed { i, it -> Comment(i.toString(), it.selectFirst(".c3 a")?.text().orEmpty(), it.selectFirst(".c6")?.text().orEmpty(), it.selectFirst(".c3")?.text().orEmpty()) }
    }
    override suspend fun favorites(page: Int): List<Comic> = if (platform == Platform.eHentai) ehItems(doc("/favorites.php?page=${page - 1}")) else htItems(doc("/users-users_fav-page-$page-c-0.html"))
    override suspend fun favorites(page: Int, folder: String): List<Comic> = if(platform == Platform.eHentai)
        ehItems(doc("/favorites.php?page=${page - 1}" + if(folder == "-1") "" else "&favcat=${folder.encoded()}")) else favorites(page)
    override suspend fun favoriteFolders(): List<FavoriteFolder> {
        if(platform != Platform.eHentai) return super.favoriteFolders()
        val document = doc("/favorites.php")
        val folders = document.select(".fp").take(10).mapIndexed { index, node -> FavoriteFolder(index.toString(), node.select("div").last()?.text()?.ifBlank { null } ?: node.text()) }
        return listOf(FavoriteFolder("-1", "全部")) + (0..9).map { index -> folders.getOrNull(index) ?: FavoriteFolder(index.toString(), "Favorite $index") }
    }
    override suspend fun postComment(comic: Comic, content: String) {
        require(platform == Platform.eHentai) { "该来源未提供发送评论接口" }
        require(http.store.vault.get(platform).cookie.isNotBlank()) { "请先登录 E-Hentai" }
        val response = http.text(comic.id, platform, body = "commenttext_new=${content.encoded()}", contentType = "application/x-www-form-urlencoded")
        val error = Jsoup.parse(response).selectFirst("p.br")?.text()
        require(error.isNullOrBlank()) { error.orEmpty() }
    }
    override suspend fun addFavorite(comic: Comic) {
        addFavorite(comic, "0")
    }
    override suspend fun addFavorite(comic: Comic, folder: String) {
        val path: String
        val body: String
        if (platform == Platform.eHentai) {
            val ids = Regex("/g/(\\d+)/([^/]+)").find(comic.id)?.groupValues ?: error("无效的漫画地址")
            path = "/gallerypopups.php?gid=${ids[1]}&t=${ids[2]}&act=addfav"
            body = "favcat=${if(folder == "-1") "0" else folder.encoded()}&favnote=&apply=Add+to+Favorites&update=1"
        } else { path = "/users-save_fav-id-${comic.id}.html"; body = "favc_id=0" }
        http.text(base + path, platform, body = body, contentType = "application/x-www-form-urlencoded")
    }
}
