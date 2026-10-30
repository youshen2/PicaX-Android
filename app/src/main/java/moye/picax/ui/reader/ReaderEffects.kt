package moye.picax.ui.reader

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import moye.picax.model.Comic
import moye.picax.ui.*

@Composable
fun ReaderEffects(comic: Comic, route: Route, chrome: Boolean, auto: Boolean, continuous: Boolean, list: LazyListState, viewportHeight: Float, visitedBooks: Set<String>, advance: () -> Unit) {
    val app = LocalApp.current; val preferences by app.store.preferences.collectAsState()
    val activity = requireNotNull(LocalActivity.current); val lifecycle = LocalLifecycleOwner.current.lifecycle
    val next by rememberUpdatedState(advance)
    LaunchedEffect(auto, continuous, preferences["reader.autoSeconds"], preferences["reader.smooth"]) {
        if(!auto) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val seconds = app.store.number("reader.autoSeconds", 6).coerceAtLeast(1)
            if(continuous && app.store.bool("reader.smooth")) {
                var previous = withFrameNanos { it }
                while(isActive) {
                    val now = withFrameNanos { it }
                    val consumed = list.scrollBy(viewportHeight * app.store.number("reader.autoDistance", 85) / 100f * ((now - previous) / 1_000_000_000f) / seconds)
                    if(kotlin.math.abs(consumed) < .1f) { next(); delay(300) }
                    previous = now
                }
            } else while(isActive) { delay(seconds * 1000L); next() }
        }
    }
    LaunchedEffect(comic.key) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        val start = android.os.SystemClock.elapsedRealtime()
        try { awaitCancellation() } finally {
            val seconds = (android.os.SystemClock.elapsedRealtime() - start) / 1000
            if(seconds >= app.store.number("time.minimum", 5)) app.store.addTime(comic, seconds)
        }
    } }
    DisposableEffect(chrome, preferences["reader.keepAwake"], preferences["reader.hideStatus"]) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if(!chrome && app.store.bool("reader.hideStatus")) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        if(app.store.bool("reader.keepAwake", true)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()); activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(comic.key) {
        if(route.queue.isNotEmpty() && app.store.bool("reader.bookToast", true)) android.widget.Toast.makeText(app.store.context, comic.title, android.widget.Toast.LENGTH_SHORT).show()
    }
    val latestComic by rememberUpdatedState(comic)
    DisposableEffect(route.instance) { onDispose {
        if(route.offline && app.store.bool(if(route.queue.isEmpty()) "reader.burnSingle" else "reader.burnList")) app.store.deleteDownloads(if(route.queue.isEmpty()) setOf(latestComic.key) else visitedBooks.toSet())
    } }
}
