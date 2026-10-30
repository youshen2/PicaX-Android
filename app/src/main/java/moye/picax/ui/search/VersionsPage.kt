package moye.picax.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*
import moye.picax.ui.library.ComicContextRow

@Composable
fun VersionsPage(route: Route) {
    val app = LocalApp.current; val data = appData()
    var comics by remember { mutableStateOf(route.queue.ifEmpty { listOfNotNull(route.comic) }) }
    var loading by remember { mutableStateOf(route.comic != null && route.queue.isEmpty()) }
    var failures by remember { mutableStateOf(emptyList<String>()) }
    var editing by remember { mutableStateOf<Comic?>(null) }
    var groupName by remember { mutableStateOf("") }; var separate by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(revision) {
        val seed = route.comic ?: return@LaunchedEffect
        loading = true; failures = emptyList(); comics = listOf(seed)
        supervisorScope { Platform.online.filter { it != seed.platform }.map { platform -> launch {
            try {
                val rows = withContext(Dispatchers.IO) { app.repository.source(platform).search("\"${TitleMatcher.searchTitle(seed.title).replace("\"", "")}\"", 1, "date") }
                comics = (comics + rows).distinctBy { it.key }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { failures += "${platform.title}：${e.message}" }
        } }.joinAll() }
        loading = false
    }
    val groups = remember(comics, data.versionOverrides, data.rules) { versionGroups(comics.filterNot(app.store::blocked), data.versionOverrides) }
    Page("多源版本", actions = { if(route.comic != null) IconAction("arrow.clockwise", "重新查找") { revision++ } }) {
        item { Group { Text("按去除附加信息后的同名标题合并；长按条目可纠正分组。", Modifier.padding(16.dp)) } }
        groups.forEach { group ->
            item(group.first().key) { Group("${group.first().title} · ${group.size} 个版本") {
                group.forEach { comic -> ActionMenu(listOf(MenuAction("纠正分组", "pencil", onClick = {
                    editing = comic; val rule = data.versionOverrides.firstOrNull { it.id == comic.key }; groupName = rule?.group.orEmpty(); separate = rule?.separates ?: false
                })), morphFromAnchor = false) { open -> ComicRow(comic, { app.navigator.detail(comic) }, open, listOf(comic.platform.title, comic.language.ifBlank { "语言未知" }, "${comic.pages} 页").joinToString(" · ")) } }
            } }
        }
        if(loading) item { Busy() }
        items(failures) { message -> Text(message, Modifier.padding(16.dp)) }
    }
    AppSheet(editing != null, "纠正分组", { editing = null }, closeLabel = "取消", actions = {
        GlasmioSheetButton({ editing?.let { comic -> app.store.update { it.copy(versionOverrides = it.versionOverrides.filterNot { old -> old.id == comic.key } + VersionOverride(comic.key, groupName.trim(), separate)) } }; editing = null }) { Text("保存") }
    }) {
        item { Group { Text(editing?.title.orEmpty(), Modifier.padding(16.dp)); GlasmioListRow("保持为独立条目", trailing = { GlasmioSwitch(separate, { separate = it }) }) } }
        if(!separate) item { Group("自定义分组", "相同名称会合并；留空使用自动分组。") { GlasmioTextField(groupName, { groupName = it }, placeholder = "分组名称") } }
    }
}
