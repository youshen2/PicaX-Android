package moye.picax.model

import kotlinx.serialization.Serializable

@Serializable
enum class Platform(val title: String, val website: String, val symbol: String) {
    picacg("PicACG", "https://picaapi.picacomic.com", "p.circle"),
    jmComic("JMComic", "https://18comic.vip", "j.circle"),
    nhentai("NHentai", "https://nhentai.net", "n.circle"),
    eHentai("E-Hentai", "https://e-hentai.org", "e.circle"),
    hitomi("Hitomi", "https://hitomi.la", "h.circle"),
    htManga("绅士漫画", "https://www.wnacg.com", "book.circle"),
    local("本地导入", "", "folder.circle");

    companion object { val online = entries.filter { it != local } }
}

@Serializable
data class Comic(
    val id: String,
    val platform: Platform,
    val title: String,
    val subtitle: String = "",
    val cover: String = "",
    val tags: List<String> = emptyList(),
    val pages: Int = 0,
    val likes: Int = 0,
    val language: String = "",
) { val key: String get() = "${platform.name}:$id" }

@Serializable data class Chapter(val id: String, val title: String, val order: Int = 1, val pages: Int = 0, val subtitle: String = "")
@Serializable data class TagGroup(val title: String, val tags: List<String>)
@Serializable data class Uploader(val id: String, val name: String, val avatar: String = "", val title: String = "", val level: Int = 0, val experience: Int = 0, val slogan: String = "", val avatarFrame: String = "")
@Serializable data class ComicDetail(val comic: Comic, val description: String = "", val chapters: List<Chapter> = listOf(Chapter("1", "开始阅读")), val related: List<Comic> = emptyList(), val updatedText: String = "", val tagGroups: List<TagGroup> = emptyList(), val uploader: Uploader? = null, val isLiked: Boolean = false)
@Serializable data class Comment(val id: String, val author: String, val content: String, val time: String = "", val avatar: String = "", val likes: Int = 0, val replies: Int = 0, val avatarFrame: String = "", val comic: Comic? = null)
data class CommentPage(val comments: List<Comment>, val hasMore: Boolean)
@Serializable data class Category(val title: String, val query: String = title, val cover: String = "", val group: String = "")
@Serializable data class Account(val platform: Platform, val username: String, val updatedAt: Long = System.currentTimeMillis())
@Serializable data class Credential(val token: String = "", val cookie: String = "", val password: String = "", val userAgent: String = "", val baseUrl: String = "")
@Serializable data class History(val comic: Comic, val chapter: Chapter, val page: Int = 0, val pageCount: Int = 0, val updatedAt: Long = System.currentTimeMillis(), val chapterCount: Int = 1)
@Serializable data class Favorite(val comic: Comic, val folder: String = "默认收藏夹", val addedAt: Long = System.currentTimeMillis())
@Serializable data class Bookmark(val id: String, val comic: Comic, val chapter: Chapter, val page: Int, val note: String = "", val image: String = "", val addedAt: Long = System.currentTimeMillis())
@Serializable data class SearchRecord(val query: String, val platforms: List<Platform>, val sort: String = "date", val savedName: String = "", val pinned: Boolean = false, val updatedAt: Long = System.currentTimeMillis())
@Serializable data class BlockRule(val text: String, val enabled: Boolean = true, val platform: Platform? = null)
@Serializable data class ReadingTime(val comic: Comic, val day: String, val seconds: Long)
@Serializable data class VersionOverride(val id: String, val group: String = "", val separates: Boolean = false)
@Serializable data class FavoriteFolder(val id: String, val title: String, val count: Int = 0)
@Serializable data class FollowUpdate(val comic: Comic, val checkedAt: Long, val chapterCount: Int, val lastChapterId: String, val hasUpdate: Boolean = false)
@Serializable data class DownloadRecord(val detail: ComicDetail, val chapter: Chapter, val urls: List<String>, val completed: Int = 0, val status: String = "等待中", val error: String = "", val createdAt: Long = System.currentTimeMillis(), val comments: List<Comment> = emptyList()) {
    val key get() = "${detail.comic.key}:${chapter.id}"
}
@Serializable data class AppData(
    val preferences: Map<String, String> = emptyMap(),
    val accounts: List<Account> = emptyList(),
    val favorites: List<Favorite> = emptyList(),
    val folders: List<String> = listOf("默认收藏夹"),
    val later: List<Comic> = emptyList(),
    val history: List<History> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val searches: List<SearchRecord> = emptyList(),
    val rules: List<BlockRule> = emptyList(),
    val readingTime: List<ReadingTime> = emptyList(),
    val downloads: List<DownloadRecord> = emptyList(),
    val versionOverrides: List<VersionOverride> = emptyList(),
    val followUpdates: List<FollowUpdate> = emptyList(),
    val laterAddedAt: Map<String, Long> = emptyMap(),
)
