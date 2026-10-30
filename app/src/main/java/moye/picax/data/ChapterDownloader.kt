package moye.picax.data

import kotlinx.coroutines.*
import moye.picax.model.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ChapterDownloader(private val repository: ComicRepository, private val progress: (String) -> Unit) {
    private val store get() = repository.store
    suspend fun download(initial: DownloadRecord) {
        var record = initial.copy(status = "下载中", error = "")
        store.download(record)
        try {
            if(record.urls.isEmpty()) {
                record = record.copy(urls = repository.source(record.detail.comic.platform).pages(record.detail.comic, record.chapter))
                require(record.urls.isNotEmpty()) { "章节没有返回图片" }; store.download(record)
            }
            store.downloadDir(record).mkdirs()
            val completed = AtomicInteger(record.urls.indices.count { store.pageFile(record, it).let { file -> file.exists() && file.length() > 0 } })
            val parallel = store.number("downloads.imageConcurrency", 3).coerceIn(1, 8)
            for(batch in record.urls.indices.toList().chunked(parallel)) {
                if(store.data.value.downloads.firstOrNull { it.key == record.key }?.status != "下载中") return
                coroutineScope { batch.map { index -> async {
                    val destination = store.pageFile(record, index)
                    if(destination.exists() && destination.length() > 0) return@async
                    var failure: Exception? = null
                    for(attempt in 0..store.number("downloads.retries", 2)) {
                        try {
                            val file = repository.images.file(record.urls[index], record.detail.comic.platform,
                                refresh = !store.bool("downloads.cache", true), limitKiB = if(store.bool("downloads.limit")) store.number("downloads.kib", 2048) else 0)
                            if(store.data.value.downloads.firstOrNull { it.key == record.key }?.status != "下载中") return@async
                            val temporary = File(destination.path + ".part")
                            file.copyTo(temporary, overwrite = true)
                            check(temporary.renameTo(destination)) { "无法保存下载图片" }
                            val count = completed.incrementAndGet()
                            store.update { state -> state.copy(downloads = state.downloads.map { if(it.key == record.key && it.status == "下载中") it.copy(completed = count) else it }) }
                            progress("${record.detail.comic.title} · $count/${record.urls.size}")
                            failure = null; break
                        } catch(e: CancellationException) { throw e }
                        catch(e: Exception) { failure = e; if(attempt < store.number("downloads.retries", 2)) delay(1000) }
                    }
                    failure?.let { throw it }
                } }.awaitAll() }
            }
            val latest = store.data.value.downloads.firstOrNull { it.key == record.key } ?: return
            if(latest.status != "下载中") return
            val comments = if(store.bool("downloads.comments") && record.detail.comic.platform != Platform.local) {
                try { repository.source(record.detail.comic.platform).chapterComments(record.detail.comic, record.chapter, 1) }
                catch(e: CancellationException) { throw e } catch(_: Exception) { emptyList() }
            } else emptyList()
            store.download(latest.copy(completed = record.urls.size, status = "已完成", comments = comments))
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            store.update { state -> state.copy(downloads = state.downloads.map { if(it.key == record.key && it.status == "下载中") it.copy(status = "失败", error = e.message.orEmpty()) else it }) }
        }
    }
}
