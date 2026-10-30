package moye.picax.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import moye.picax.data.AppStore
import moye.picax.data.ComicRepository
import moye.picax.model.*

@Serializable data class Route(val screen: String, val title: String = "", val parameter: String = "", val platform: Platform = Platform.picacg, val comic: Comic? = null, val chapter: Chapter? = null, val queue: List<Comic> = emptyList(), val instance: String = java.util.UUID.randomUUID().toString(), val offline: Boolean = false)
class Navigator(val store: AppStore, val stack: SnapshotStateList<String>) {
    private val roots = mutableMapOf(stack.first().let { store.json.decodeFromString<Route>(it).screen } to stack.toList())
    val searchSession by lazy { moye.picax.ui.search.SearchSession(store) }
    private val currentRoute = derivedStateOf { store.json.decodeFromString<Route>(stack.last()) }
    val current get() = currentRoute.value
    fun open(route: Route) { stack.add(store.json.encodeToString(route)) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(name: String) {
        if(current.screen == name) return
        roots[store.json.decodeFromString<Route>(stack.first()).screen] = stack.toList()
        val restored = roots.getOrPut(name) { listOf(store.json.encodeToString(Route(name))) }
        stack.clear(); stack.addAll(restored)
    }
    fun detail(comic: Comic, offline: Boolean = false) = open(Route("detail", comic.title, comic = comic, platform = comic.platform, offline = offline))
    fun reader(comic: Comic, chapter: Chapter, queue: List<Comic> = emptyList(), offline: Boolean = false) = open(Route("reader", comic.title, comic = comic, chapter = chapter, queue = queue, offline = offline))
}
class AppEnvironment(val store: AppStore, val repository: ComicRepository, val navigator: Navigator)
val LocalApp = staticCompositionLocalOf<AppEnvironment> { error("PicaX application is missing") }

@Composable fun appData(): AppData = LocalApp.current.store.data.collectAsState().value
