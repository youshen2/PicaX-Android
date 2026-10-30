package moye.picax.data

import android.net.Uri
import kotlinx.coroutines.*
import moye.picax.data.source.*
import moye.picax.model.*

class ComicRepository(val store: AppStore) {
    val http = SourceHttp(store)
    val images by lazy { PageImages(this) }
    val coverPalette by lazy { CoverPalette(images) }
    val tags by lazy { TagCatalog(store.context) }
    private val categoryLists = android.util.LruCache<Platform, List<Category>>(8)
    fun invalidateCategories() { categoryLists.evictAll() }
    suspend fun categories(platform: Platform): List<Category> = categoryLists.get(platform) ?: source(platform).categories().also { categoryLists.put(platform, it) }
    private val details = android.util.LruCache<String, ComicDetail>(40)
    private val pageLists = android.util.LruCache<String, List<String>>(32)
    private val sources = listOf(PicacgSource(http), JmSource(http), NhentaiSource(http), WebSource(Platform.eHentai, http), HitomiSource(http), WebSource(Platform.htManga, http)).associateBy { it.platform }
    fun source(platform: Platform): SourceClient = sources.getValue(platform)
    suspend fun detail(comic: Comic, offline: Boolean = false): ComicDetail {
        if(offline || comic.platform == Platform.local) {
            val downloaded = store.data.value.downloads.filter { it.detail.comic.key == comic.key && it.status == "已完成" }
            require(downloaded.isNotEmpty()) { "本地没有可阅读的章节" }
            return downloaded.first().detail.copy(chapters = downloaded.map { it.chapter }.sortedBy { it.order })
        }
        details.get(comic.key)?.let { return it }
        return source(comic.platform).detail(comic).also { details.put(comic.key, it) }
    }
    fun invalidateDetail(comic: Comic) { details.remove(comic.key) }
    suspend fun pages(comic: Comic, chapter: Chapter, offline: Boolean = false): List<String> {
        val downloaded = store.data.value.downloads.firstOrNull { it.detail.comic.key == comic.key && it.chapter.id == chapter.id && it.status == "已完成" }
        if (downloaded != null) return downloaded.urls.indices.map { Uri.fromFile(store.pageFile(downloaded, it)).toString() }
        require(!offline && comic.platform != Platform.local) { "本地文件已缺失，请重新导入或下载" }
        val key = "${comic.key}:${chapter.id}"
        pageLists.get(key)?.let { return it }
        return source(comic.platform).pages(comic, chapter).also { pageLists.put(key, it) }
    }
    fun fromLink(value: String): Comic {
        val text = value.trim()
        if (text.matches(Regex("(?i)(JM)?\\d+"))) return Comic(text.replace(Regex("(?i)JM"), ""), Platform.jmComic, text)
        val uri = Uri.parse(text)
        val host = uri.host.orEmpty().lowercase()
        val platform = when {
            host.contains("nhentai") -> Platform.nhentai
            host.contains("e-hentai") || host.contains("exhentai") -> Platform.eHentai
            host.contains("hitomi") -> Platform.hitomi
            host.contains("wnacg") -> Platform.htManga
            host.contains("picacg") || host.contains("picacomic") -> Platform.picacg
            host.contains("18comic") || host.contains("jmcomic") -> Platform.jmComic
            else -> error("暂无法识别这个漫画链接")
        }
        val id = when(platform) {
            Platform.eHentai -> text.substringBefore('?')
            Platform.picacg -> uri.pathSegments.lastOrNull().orEmpty()
            else -> Regex("(?:/g/|/album/|aid-|/galleries/|/reader/)?(\\d+)(?:\\.html)?").findAll(uri.path.orEmpty()).lastOrNull()?.groupValues?.get(1).orEmpty()
        }
        require(id.isNotEmpty()) { "链接缺少漫画编号" }
        return Comic(id, platform, id)
    }
}
