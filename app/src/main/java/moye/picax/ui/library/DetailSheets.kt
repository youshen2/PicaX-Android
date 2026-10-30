package moye.picax.ui.library

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.data.DownloadService
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun DetailChapterSheet(visible: Boolean, chapters: List<Chapter>, onDismiss: () -> Unit, onSelect: (Chapter) -> Unit) {
    val data = appData()
    val descending = data.preferences["detail.chapterDescending"] == "true"
    AppSheet(visible, "章节", onDismiss, closeIcon = true) {
        item { ChapterSectionHeader(chapters.size) }
        item {
            GlasmioGroupedList {
                (if(descending) chapters.reversed() else chapters).forEach { chapter -> ChapterRow(chapter, onClick = { onSelect(chapter) }) }
            }
        }
    }
}

@Composable
fun DetailDownloadSheet(visible: Boolean, detail: ComicDetail?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val data = appData()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val downloaded = data.downloads.filter { it.detail.comic.key == detail?.comic?.key && it.status == "已完成" }.map { it.chapter.id }.toSet()
    val available = detail?.chapters.orEmpty().filterNot { it.id in downloaded }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(visible, detail) { if(visible) selected = if(available.size == 1) available.map { it.id }.toSet() else emptySet() }
    fun enqueue(chapters: List<Chapter>) {
        val value = detail ?: return
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        DownloadService.enqueue(app.store.context, value, chapters)
        onDismiss()
    }
    AppSheet(visible, "下载漫画", onDismiss, closeLabel = "关闭", footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlasmioButton({ enqueue(available) }, Modifier.weight(1f), enabled = available.isNotEmpty()) { Text("下载全部") }
            GlasmioButton({ enqueue(available.filter { it.id in selected }) }, Modifier.weight(1f), enabled = selected.isNotEmpty(), emphasis = GlassEmphasis.Prominent) { Text("下载所选（${selected.size}）") }
        }
    }) {
        item { GlasmioSectionTitle(detail?.comic?.title.orEmpty()) }
        items(detail?.chapters.orEmpty(), key = { it.id }) { chapter ->
            val complete = chapter.id in downloaded
            GlasmioGroupedList {
                GlasmioListRow(chapter.title, detail = if(complete) "已下载" else null, enabled = !complete, onClick = {
                    selected = if(chapter.id in selected) selected - chapter.id else selected + chapter.id
                }, showDisclosure = false, leading = {
                    SfIcon(if(complete || chapter.id in selected) "checkmark.circle.fill" else "circle", null,
                        tint = if(complete) GlasmioTheme.colors.secondaryContent else GlasmioTheme.colors.accent)
                })
            }
        }
    }
}

@Composable
fun DetailFavoriteSheet(visible: Boolean, comic: Comic, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val data = appData()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloudFolders by remember { mutableStateOf(listOf(FavoriteFolder("0", comic.platform.title))) }
    LaunchedEffect(visible, comic.platform) {
        if(visible && comic.platform != Platform.local && data.accounts.any { it.platform == comic.platform }) {
            try { cloudFolders = withContext(Dispatchers.IO) { app.repository.source(comic.platform).favoriteFolders() } }
            catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
        }
    }
    AppSheet(visible, "收藏", onDismiss, closeLabel = "关闭") {
        item {
            Group("本地收藏") {
                data.folders.forEach { folder ->
                    GlasmioListRow(folder, systemName = "folder", detail = if(data.favorites.any { it.comic.key == comic.key && it.folder == folder }) "已收藏" else null, onClick = {
                        app.store.update { it.copy(favorites = listOf(Favorite(comic, folder)) + it.favorites.filterNot { favorite -> favorite.comic.key == comic.key }) }
                        onDismiss()
                    })
                }
            }
        }
        if(comic.platform != Platform.local) item {
            Group("云端收藏") {
                cloudFolders.forEach { folder -> GlasmioListRow(folder.title, subtitle = if(saving) "正在保存…" else data.accounts.firstOrNull { it.platform == comic.platform }?.username ?: "需先登录平台账号", enabled = !saving, systemName = "icloud", onClick = {
                    scope.launch {
                        saving = true; error = null
                        try { withContext(Dispatchers.IO) { app.repository.source(comic.platform).addFavorite(comic, folder.id) }; onDismiss() }
                        catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
                        finally { saving = false }
                    }
                }) }
            }
        }
        if(error != null) item { Text(error!!, color = GlasmioTheme.colors.destructive) }
    }
}

@Composable
fun DetailCoverSheet(visible: Boolean, comic: Comic, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(visible) { if(visible) { scale = 1f; offset = Offset.Zero } }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if(scale > 1f) offset + pan else Offset.Zero
    }
    CompositionLocalProvider(LocalGlassColors provides defaultGlassColors(true, comic.platform.accent())) {
        GlasmioBottomSheet(onDismiss, visible = visible, title = "封面", topBar = {
            GlasmioSheetTopToolbar("", actions = { IconAction("xmark", "关闭", onDismiss) })
        }, contentBackground = { Box(Modifier.fillMaxSize().background(Color.Black)) }) {
            Box(Modifier.fillMaxSize().background(Color.Black).padding(LocalGlasmioSheetContentPadding.current).padding(16.dp).clipToBounds()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }) {
                AsyncImage(comic.cover, "${comic.title} 封面预览", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }.transformable(transform, canPan = { scale > 1f }))
            }
        }
    }
}
