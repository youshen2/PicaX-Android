package moye.picax.ui.library

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

@Composable fun ExplorePage() {
    val app = LocalApp.current
    var platform by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Platform.valueOf(app.store.setting("explore.platform", "picacg"))) }
    Page("发现", root = true, actions = { IconAction("gearshape", "设置") { app.navigator.open(Route("settings")) } }) {
        item { PlatformPicker(platform) { platform = it; app.store.set("explore.platform", it.name) } }
        item { Group("发现") {
            if(platform in listOf(Platform.picacg, Platform.jmComic, Platform.hitomi)) DiscoveryRow("随机漫画", "换一种方式发现漫画", "shuffle", platform, "random")
            DiscoveryRow("最新更新", "浏览最新上传的漫画", "clock", platform, "latest")
        } }
        item { Group("热门") {
            DiscoveryRow("今日热门", "最近一天的热门漫画", "flame", platform, "popular-today")
            if(platform != Platform.eHentai) {
                DiscoveryRow("本周热门", "最近一周的热门漫画", "chart.bar", platform, "popular-week")
                DiscoveryRow("本月热门", "最近一个月的热门漫画", "chart.bar", platform, "popular-month")
                if(platform == Platform.nhentai) DiscoveryRow("总排行榜", "所有时间的热门漫画", "trophy", platform, "popular")
                if(platform == Platform.hitomi) DiscoveryRow("本年热门", "最近一年的热门漫画", "trophy", platform, "popular-year")
            }
        } }
    }
}
@Composable private fun DiscoveryRow(title: String, subtitle: String, symbol: String, platform: Platform, kind: String) {
    val app = LocalApp.current
    GlasmioListRow(title, subtitle = subtitle, systemName = symbol, onClick = { app.navigator.open(Route("results", title, kind, platform)) })
}

@Composable fun CategoriesPage() {
    val app = LocalApp.current
    var platform by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Platform.picacg) }
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(platform, retry) {
        categories = null; error = null
        try { categories = withContext(Dispatchers.IO) { app.repository.categories(platform) } }
        catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
    }
    Page("分类", root = true, actions = { IconAction("gearshape", "设置") { app.navigator.open(Route("settings")) } }) {
        item { PlatformPicker(platform) { platform = it } }
        when {
            error != null -> item { EmptyPage("加载失败", error!!, "exclamationmark.triangle", "重试") { retry++ } }
            categories == null -> item { Busy() }
            categories!!.isEmpty() -> item { EmptyPage("暂无分类", "该来源没有返回分类", "square.grid.2x2") }
            else -> categories!!.groupBy { it.group }.forEach { (group, entries) -> item {
                Group(group.ifEmpty { "分类" }) { entries.forEach { category ->
                    GlasmioListRow(category.title, onClick = { app.navigator.open(Route("search", parameter = category.query, platform = platform)) }, leading = {
                        if(category.cover.isNotBlank() && app.store.bool("categories.covers", true)) coil3.compose.AsyncImage(category.cover, null, Modifier.size(52.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else io.github.neoruaa.glasmio.icons.SfIcon("tag", null)
                    })
                } }
            } }
        }
    }
}

@Composable fun ResultsPage(route: Route) {
    val app = LocalApp.current
    val data = appData()
    var comics by remember { mutableStateOf(emptyList<Comic>()) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(page, revision) {
        loading = true; error = null
        try {
            val list = withContext(Dispatchers.IO) { when {
                route.parameter.startsWith("cloud") -> app.repository.source(route.platform).favorites(page, route.parameter.substringAfter(':', "0"))
                route.parameter.startsWith("category:") && route.platform == Platform.jmComic -> app.repository.source(route.platform).explore(route.parameter, page)
                else -> app.repository.source(route.platform).explore(route.parameter, page)
            } }
            comics = (if(page == 1) list else comics + list).distinctBy { it.key }
            hasMore = list.isNotEmpty()
        } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
        finally { loading = false }
    }
    ComicResultsSurface(route.title, comics, actions = {
        IconAction("square.stack", "多源版本") { app.navigator.open(Route("versions", queue = comics)) }
        IconAction("arrow.clockwise", "刷新") { page = 1; revision++ }
    }, footer = {
        if(loading) Busy()
        else if(error != null) EmptyPage("加载失败", error!!, "exclamationmark.triangle", "重试") { revision++ }
        else if(comics.isEmpty()) EmptyPage("暂无漫画", "接口返回为空", "book")
        else if(hasMore) GlasmioButton({ page++ }, Modifier.fillMaxWidth()) { Text("加载更多") }
    })
}

@Composable fun ComicContextRow(comic: Comic, detail: String? = null, extraActions: List<MenuAction> = emptyList()) {
    val app = LocalApp.current
    ActionMenu(extraActions + comicMenuActions(comic), morphFromAnchor = false) { open -> ComicRow(comic, { app.navigator.detail(comic) }, open, detail) }
}
@Composable fun comicMenuActions(comic: Comic): List<MenuAction> {
    val app = LocalApp.current
    val data = appData()
    return listOf(
        MenuAction("阅读", "book", onClick = { app.navigator.reader(comic, Chapter("1", "开始阅读")) }),
        MenuAction(if(data.favorites.any { it.comic.key == comic.key }) "取消本地收藏" else "加入本地收藏", "heart", onClick = { app.store.favorite(comic) }),
        MenuAction(if(data.later.any { it.key == comic.key }) "移出稍后再读" else "加入稍后再读", "bookmark", onClick = { app.store.later(comic) }),
        MenuAction("查看详情", "info.circle", onClick = { app.navigator.detail(comic) }),
        MenuAction("其他来源", "square.stack", onClick = { app.navigator.open(Route("versions", comic = comic)) }),
    )
}
