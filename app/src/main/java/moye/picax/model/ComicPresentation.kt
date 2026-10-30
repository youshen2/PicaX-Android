package moye.picax.model

import java.net.URI

data class ComicCopyAction(val title: String, val value: String, val symbol: String)

data class LibraryPresentation(
    val history: Map<String, History>,
    val favorites: Map<String, Favorite>,
    val preferences: Map<String, String>,
)

/** Matches the platform-specific copy action in the Swift detail screen. */
fun Comic.copyAction(): ComicCopyAction? {
    if(platform == Platform.jmComic) return ComicCopyAction("复制车牌号", "jm${id.removePrefix("jm").removePrefix("JM")}", "number.square")
    if(platform == Platform.picacg || platform == Platform.local) return null
    val existing = runCatching { URI(id) }.getOrNull()?.takeIf { it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() }
    val number = Regex("\\d+").find(id)?.value
    val link = existing?.toString() ?: when(platform) {
        Platform.nhentai -> number?.let { "${platform.website}/g/$it/" }
        Platform.hitomi -> number?.let { "${platform.website}/galleries/$it.html" }
        Platform.htManga -> number?.let { "${platform.website}/photos-index-page-1-aid-$it.html" }
        else -> null
    }
    return link?.let { ComicCopyAction("复制分享链接", it, "link") }
}

val History.progressText: String
    get() = if(pageCount == 0) "查看过详情" else "读到 E${chapter.order} · P${page + 1}"
