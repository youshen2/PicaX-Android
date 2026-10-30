package moye.picax.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.ui.*
import io.github.neoruaa.glasmio.core.GlasmioTheme
import kotlinx.coroutines.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable fun FavoritesPage() {
    val app = LocalApp.current
    val data = appData()
    var addFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deletingFolder by remember { mutableStateOf<String?>(null) }
    Page("收藏", root = true, actions = {
        IconAction("plus", "新建收藏夹") { addFolder = true }
        IconAction("gearshape", "设置") { app.navigator.open(Route("settings")) }
    }) {
        item { Group("本地收藏夹") {
            data.folders.forEach { folder ->
                ActionMenu(listOf(
                    MenuAction("重命名", "pencil", enabled = folder != "默认收藏夹", onClick = { renaming = folder }),
                    MenuAction("删除收藏夹", "trash", enabled = folder != "默认收藏夹", destructive = true, onClick = { deletingFolder = folder })
                ), morphFromAnchor = false) { open ->
                    GlasmioListRow(folder, detail = data.favorites.count { it.folder == folder }.toString(), systemName = "folder",
                        onClick = { app.navigator.open(Route("library", folder, "folder:$folder")) }, trailing = { IconAction("ellipsis", "管理$folder", open) })
                }
            }
        } }
        item { Group("平台收藏") {
            if(data.accounts.isEmpty()) GlasmioListRow("管理平台账号", subtitle = "登录后查看平台收藏", systemName = "person.2", onClick = { app.navigator.open(Route("accounts")) })
            data.accounts.forEach { account -> GlasmioListRow(account.platform.title, subtitle = account.username, systemName = account.platform.symbol,
                onClick = { if(account.platform in listOf(Platform.jmComic, Platform.eHentai)) app.navigator.open(Route("folders", platform = account.platform))
                    else app.navigator.open(Route("results", "${account.platform.title} 收藏", "cloud", account.platform)) }) }
        } }
    }
    if(addFolder) TextPrompt("新建收藏夹", "收藏夹名称", dismiss = { addFolder = false }) { name -> app.store.update { it.copy(folders = (it.folders + name).distinct()) } }
    if(renaming != null) TextPrompt("重命名收藏夹", initial = renaming.orEmpty(), dismiss = { renaming = null }) { name ->
        val previous = renaming
        if(name.isNotBlank()) app.store.update { it.copy(folders = it.folders.map { folder -> if(folder == previous) name else folder }.distinct(), favorites = it.favorites.map { favorite -> if(favorite.folder == previous) favorite.copy(folder = name) else favorite }) }
    }
    if(deletingFolder != null) GlasmioAlertDialog({ deletingFolder = null }, "删除收藏夹？", "收藏的漫画会移到默认收藏夹。", buttons = listOf(
        GlasmioAlertButtonSpec("取消", { deletingFolder = null }), GlasmioAlertButtonSpec("删除", {
            val folder = deletingFolder; app.store.update { it.copy(folders = it.folders - folder.orEmpty(), favorites = it.favorites.map { favorite -> if(favorite.folder == folder) favorite.copy(folder = "默认收藏夹") else favorite }) }; deletingFolder = null
        }, GlasmioAlertButtonRole.Destructive)))

}

@Composable fun LibraryPage(route: Route) {
    val app = LocalApp.current
    val data = appData()
    val comics = when {
        route.parameter == "history" -> data.history.map { it.comic }
        route.parameter == "later" -> data.later
        route.parameter.startsWith("folder:") -> data.favorites.filter { it.folder == route.parameter.substringAfter(':') }.map { it.comic }
        else -> data.favorites.map { it.comic }
    }
    var confirmClear by remember { mutableStateOf(false) }
    Page(route.title, actions = {
        if(comics.isNotEmpty() && route.parameter != "history") {
            IconAction("play.fill", "阅读全部") { app.navigator.reader(comics.first(), Chapter("1", "开始阅读"), comics) }
            IconAction("trash", "清空列表") { confirmClear = true }
        }
    }) {
        if(comics.isEmpty()) item { EmptyPage("暂无${route.title}", "添加的漫画会显示在这里", "books.vertical") }
        items(comics, key = { it.key }) { comic ->
            var revealed by remember { mutableStateOf(false) }
            fun remove() { app.store.update { state -> when {
                route.parameter == "history" -> state.copy(history = state.history.filterNot { it.comic.key == comic.key })
                route.parameter == "later" -> state.copy(later = state.later.filterNot { it.key == comic.key })
                else -> state.copy(favorites = state.favorites.filterNot { it.comic.key == comic.key })
            } } }
            GlasmioListSwipeActions(revealed, { revealed = it }, actions = {
                GlasmioListSwipeAction("trash", ::remove, GlasmioTheme.colors.destructive, label = "删除")
            }) { GlasmioGroupedList {
                val history = data.history.firstOrNull { it.comic.key == comic.key }
                if(route.parameter == "history" && history != null) HistoryRow(history) { app.navigator.detail(comic) }
                else ComicContextRow(comic,
                detail = if(route.parameter == "history") data.history.firstOrNull { it.comic.key == comic.key }?.let { "${it.chapter.title} · 第 ${it.page + 1} / ${it.pageCount.coerceAtLeast(1)} 页" } else null,
                extraActions = listOf(MenuAction("移出列表", "trash", destructive = true, onClick = ::remove)))
            } }
        }
    }
    if(confirmClear) GlasmioAlertDialog({ confirmClear = false }, "清空${route.title}？", "此操作将清空当前列表。", buttons = listOf(
        GlasmioAlertButtonSpec("取消", { confirmClear = false }, GlasmioAlertButtonRole.Cancel),
        GlasmioAlertButtonSpec("清空", { app.store.update { state -> when {
            route.parameter == "history" -> state.copy(history = emptyList())
            route.parameter == "later" -> state.copy(later = emptyList())
            else -> state.copy(favorites = state.favorites.filterNot { it.comic in comics })
        } }; confirmClear = false }, GlasmioAlertButtonRole.Destructive)
    ))
}

@Composable fun FollowUpdatesPage() {
    val app = LocalApp.current
    val data = appData()
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var settings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    fun check() {
        if(running) return
        running = true; progress = 0; errors = emptyList()
        scope.launch {
            try { errors = moye.picax.data.FollowUpdates.check(app.repository) { done, count -> progress = done; total = count }.errors }
            finally { running = false }
        }
    }
    val updated = data.followUpdates.filter { it.hasUpdate && data.favorites.any { favorite -> favorite.comic.key == it.comic.key } }
    Page("追更", actions = {
        IconAction("gearshape", "追更设置") { settings = true }
        IconAction("arrow.clockwise", "检查更新", ::check)
    }) {
        item { Group("检查更新") { GlasmioListRow(if(running) "正在检查 $progress / $total" else "检查本地收藏", subtitle = "按章节与页数变化检查更新", onClick = ::check) } }
        items(updated, key = { it.comic.key }) { entry -> GlasmioGroupedList { ComicRow(entry.comic, { app.navigator.detail(entry.comic) }, detail = "更新至 ${entry.chapterCount} 章") } }
        if(!running && updated.isEmpty()) item { EmptyPage("当前没有更新", "首次检查建立记录，后续更新会保留到阅读时。", "checkmark.circle") }
        errors.forEach { message -> item { Text(message, Modifier.padding(12.dp)) } }
    }
    AppSheet(settings, "追更设置", { settings = false }) {
        item { Group("自动检查") {
            moye.picax.ui.settings.SwitchSetting("自动追更", "follow.enabled", false)
            moye.picax.ui.settings.NumberSetting("检查间隔（分钟）", "follow.minutes", 1440, 15..10080)
            moye.picax.ui.settings.SwitchSetting("更新通知", "follow.notifications", true)
            GlasmioListRow("允许通知", onClick = { permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) })
        } }
    }
}
