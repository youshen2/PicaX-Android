package moye.picax.ui.reader

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.picax.data.ComicRepository
import moye.picax.model.*

data class ReaderPageRef(val comic: Comic, val chapter: Chapter, val index: Int, val url: String, val chapterCount: Int, val isComments: Boolean = false) {
    val key get() = "${comic.key}:${chapter.id}:$index${if(isComments) ":comments" else ""}"
}

@Stable
class ReaderSession(private val repository: ComicRepository, val offline: Boolean) {
    var pages by mutableStateOf(emptyList<ReaderPageRef>()); private set
    var loading by mutableStateOf(false); private set
    var extending by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private var ranges = emptyMap<String, IntRange>()
    private fun replacePages(value: List<ReaderPageRef>) {
        val bounds = linkedMapOf<String, IntRange>()
        value.forEachIndexed { index, ref -> if(!ref.isComments) { val key = "${ref.comic.key}:${ref.chapter.id}"; bounds[key] = (bounds[key]?.first ?: index)..index } }
        ranges = bounds; pages = value
    }
    private val details = mutableMapOf<String, ComicDetail>()
    suspend fun detail(comic: Comic): ComicDetail = details[comic.key] ?: withContext(Dispatchers.IO) { repository.detail(comic, offline) }.also { details[comic.key] = it }
    private suspend fun chapterPages(comic: Comic, chapter: Chapter): List<ReaderPageRef> {
        val book = detail(comic)
        val images = withContext(Dispatchers.IO) { repository.pages(book.comic, chapter, offline) }.mapIndexed { index, url -> ReaderPageRef(book.comic, chapter, index, url, book.chapters.size) }
        return withComments(images)
    }
    suspend fun open(comic: Comic, requested: Chapter): Int {
        loading = true; error = null; replacePages(emptyList())
        return try {
            val book = detail(comic)
            val chapter = book.chapters.firstOrNull { it.id == requested.id } ?: book.chapters.getOrNull(requested.order - 1) ?: book.chapters.first()
            replacePages(chapterPages(book.comic, chapter))
            require(pages.isNotEmpty()) { "该章节没有返回阅读页面" }
            val history = repository.store.data.value.history.firstOrNull { it.comic.key == comic.key && it.chapter.id == chapter.id }
            (history?.page ?: 0).coerceIn(pages.indices)
        } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message; 0 }
        finally { loading = false }
    }
    suspend fun adjacent(ref: ReaderPageRef, delta: Int, queue: List<Comic>, crossBooks: Boolean): Pair<Comic, Chapter>? {
        val book = detail(ref.comic)
        val index = book.chapters.indexOfFirst { it.id == ref.chapter.id }
        book.chapters.getOrNull(index + delta)?.let { return book.comic to it }
        if(!crossBooks) return null
        val next = queue.getOrNull(queue.indexOfFirst { it.key == ref.comic.key } + delta) ?: return null
        val nextDetail = detail(next)
        return nextDetail.comic to (if(delta > 0) nextDetail.chapters.first() else nextDetail.chapters.last())
    }
    suspend fun extend(delta: Int, queue: List<Comic>, crossBooks: Boolean): Int {
        if(extending || pages.isEmpty()) return 0
        extending = true
        return try {
            val edge = if(delta > 0) pages.last() else pages.first()
            val target = adjacent(edge, delta, queue, crossBooks) ?: return 0
            if(pages.any { it.comic.key == target.first.key && it.chapter.id == target.second.id }) return 0
            val added = chapterPages(target.first, target.second)
            replacePages(if(delta > 0) pages + added else added + pages)
            added.size
        } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message; 0 }
        finally { extending = false }
    }
    private fun withComments(images: List<ReaderPageRef>): List<ReaderPageRef> {
        if(!repository.store.bool("reader.comments")) return images
        return images.groupBy { "${it.comic.key}:${it.chapter.id}" }.values.flatMap { chapter ->
            val last = chapter.last()
            if(last.comic.platform in listOf(Platform.picacg, Platform.jmComic, Platform.eHentai) || offline) chapter + last.copy(isComments = true) else chapter
        }
    }
    fun refreshComments() { replacePages(withComments(pages.filterNot { it.isComments })) }
    fun chapterRange(ref: ReaderPageRef): IntRange {
        return ranges["${ref.comic.key}:${ref.chapter.id}"] ?: IntRange.EMPTY
    }
}
