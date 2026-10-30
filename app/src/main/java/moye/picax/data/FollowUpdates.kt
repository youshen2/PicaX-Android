package moye.picax.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.picax.model.*

data class FollowCheck(val changed: Int, val errors: List<String>)

object FollowUpdates {
    suspend fun check(repository: ComicRepository, progress: (Int, Int) -> Unit = { _, _ -> }): FollowCheck = withContext(Dispatchers.IO) {
        val store = repository.store
        var changed = 0
        val errors = mutableListOf<String>()
        val favorites = store.data.value.favorites.filter { it.comic.platform != Platform.local }
        favorites.forEachIndexed { index, favorite ->
            try {
                repository.invalidateDetail(favorite.comic)
                val detail = repository.detail(favorite.comic)
                val previous = store.data.value.followUpdates.firstOrNull { it.comic.key == favorite.comic.key }
                val newest = detail.chapters.lastOrNull()?.id.orEmpty()
                val isNew = previous != null && (previous.chapterCount != detail.chapters.size || previous.lastChapterId != newest || previous.comic.pages != detail.comic.pages)
                if(isNew && previous.hasUpdate != true) changed++
                val record = FollowUpdate(detail.comic, System.currentTimeMillis(), detail.chapters.size, newest, isNew || previous?.hasUpdate == true)
                store.update { it.copy(followUpdates = it.followUpdates.filterNot { old -> old.comic.key == favorite.comic.key } + record) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { errors += "${favorite.comic.title}：${e.message}" }
            progress(index + 1, favorites.size)
        }
        store.set("follow.lastChecked", System.currentTimeMillis())
        FollowCheck(changed, errors)
    }
}
