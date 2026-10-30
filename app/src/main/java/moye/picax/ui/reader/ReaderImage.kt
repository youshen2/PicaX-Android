package moye.picax.ui.reader

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import io.github.neoruaa.glasmio.ui.GlasmioButton
import kotlinx.coroutines.*
import moye.picax.data.PageImages
import moye.picax.ui.*
import java.io.File

@Composable
fun ReaderImage(ref: ReaderPageRef, images: PageImages, paged: Boolean, modifier: Modifier = Modifier, tap: (Offset) -> Unit, doubleTap: () -> Unit) {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    val context = LocalContext.current
    var revision by remember(ref.key) { mutableIntStateOf(0) }
    var image by remember(ref.key) { mutableStateOf<File?>(null) }
    var ratio by remember(ref.key) { mutableFloatStateOf(.7f) }
    var error by remember(ref.key) { mutableStateOf<String?>(null) }
    var scale by remember(ref.key) { mutableFloatStateOf(1f) }
    var offset by remember(ref.key) { mutableStateOf(Offset.Zero) }
    var previousScale by remember { mutableFloatStateOf(1f) }
    var temporary by remember { mutableStateOf(false) }
    val onTap by rememberUpdatedState(tap); val onDoubleTap by rememberUpdatedState(doubleTap)
    val transform = rememberTransformableState { zoom, pan, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset = if(scale > 1) offset + pan else Offset.Zero }
    LaunchedEffect(ref.url, revision) {
        image = null; error = null
        for(attempt in 0..app.store.number("reader.retry", 2)) {
            try {
                val file = images.file(ref.url, ref.comic.platform)
                if(!paged) ratio = images.aspectRatio(file)
                image = file; return@LaunchedEffect
            } catch(e: CancellationException) { throw e } catch(e: Exception) {
                error = e.message
                if(attempt < app.store.number("reader.retry", 2)) delay(app.store.number("reader.retryDelay", 1000).toLong())
            }
        }
    }
    val base = LocalViewConfiguration.current
    val configuration = remember(base, preferences["reader.longDelay"]) { object : ViewConfiguration by base { override val longPressTimeoutMillis = app.store.number("reader.longDelay", 300).toLong() } }
    val zoomed = scale > 1f
    val request = remember(image, zoomed) { ImageRequest.Builder(context).data(image).apply { if(zoomed) size(Size.ORIGINAL) }.build() }
    val layout = if(paged) modifier.fillMaxSize() else modifier.fillMaxWidth().aspectRatio(ratio)
    CompositionLocalProvider(LocalViewConfiguration provides configuration) {
        Box(layout.clipToBounds().pointerInput(ref.key, preferences) {
            detectTapGestures(onPress = {
                tryAwaitRelease()
                if(temporary) { scale = previousScale; offset = Offset.Zero; temporary = false }
            }, onLongPress = if(app.store.bool("reader.zoomLong", true)) ({ previousScale = scale; scale = app.store.number("reader.longScale", 175) / 100f; temporary = true }) else null,
                onDoubleTap = {
                    if(app.store.setting("reader.uiToggle", "single") == "double") onDoubleTap()
                    else if(app.store.bool("reader.zoomDouble", true)) { scale = if(scale > 1) 1f else app.store.number("reader.doubleScale", 175) / 100f; offset = Offset.Zero }
                    else onDoubleTap()
                }, onTap = { onTap(Offset(it.x / size.width.coerceAtLeast(1), it.y / size.height.coerceAtLeast(1))) })
        }, contentAlignment = Alignment.Center) {
            when {
                image != null -> AsyncImage(request, "漫画页面", Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    alpha = if(app.store.bool("reader.dark", true) && app.store.bool("reader.dim")) .9f else 1f
                }.transformable(transform, canPan = { scale > 1f }, enabled = app.store.bool("reader.zoomPinch", true)), contentScale = if(paged) ContentScale.Fit else ContentScale.FillWidth)
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(error!!, color = Color.Gray, fontSize = 13.sp); GlasmioButton({ revision++ }) { Text("重试") } }
                else -> CircularProgressIndicator()
            }
        }
    }
}
