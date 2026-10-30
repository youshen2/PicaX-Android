package moye.picax.ui.library

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun CloudFoldersPage(platform: Platform) {
    val app = LocalApp.current
    var folders by remember { mutableStateOf<List<FavoriteFolder>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }; var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(platform, revision) {
        try { folders = withContext(Dispatchers.IO) { app.repository.source(platform).favoriteFolders() }; error = null }
        catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
    }
    Page("${platform.title} 收藏夹", actions = { IconAction("arrow.clockwise", "刷新") { revision++ } }) {
        when {
            error != null -> item { EmptyPage("加载失败", error!!, action = "重试") { revision++ } }
            folders == null -> item { Busy() }
            folders!!.isEmpty() -> item { EmptyPage("暂无收藏夹", "这个平台当前没有返回收藏夹", "folder") }
            else -> items(folders!!, key = { it.id }) { folder -> Group { GlasmioListRow(folder.title, subtitle = "${platform.title} 收藏夹", systemName = "folder", onClick = {
                app.navigator.open(Route("results", folder.title, "cloud:${folder.id}", platform))
            }) } }
        }
    }
}
