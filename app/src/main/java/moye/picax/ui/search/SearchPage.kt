package moye.picax.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*
import moye.picax.ui.library.ComicResultsSurface
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

@Composable fun SearchPage(route: Route) {
    val app = LocalApp.current
    val data = appData()
    val scope = rememberCoroutineScope()
    val session = remember(route.instance) { if(route.parameter.isBlank()) app.navigator.searchSession else SearchSession(app.store, route) }
    var query by session.query
    var platforms by session.platforms
    var sort by session.sort
    var language by session.language
    var results by session.results
    var searched by session.searched
    var page by session.page
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(emptyMap<Platform, String>()) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var showSaveName by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeQuery by session.activeQuery
    var activePlatforms by session.activePlatforms
    var hasMore by session.hasMore
    var suggestions by remember { mutableStateOf(emptyList<Category>()) }
    LaunchedEffect(query, platforms, data.preferences["search.suggestions"]) {
        suggestions = if(app.store.bool("search.suggestions", true) && Platform.nhentai in platforms && query.isNotBlank() && query != activeQuery)
            withContext(Dispatchers.IO) { app.repository.tags.suggestions(query) } else emptyList()
    }
    fun search(reset: Boolean = true, retryPlatform: Platform? = null) {
        val entered = if(reset) query.trim() else activeQuery
        if(entered.isBlank()) return
        val combinations = try { SearchExpression.clauses(entered) } catch(e: IllegalArgumentException) { error = e.message; return }
        currentJob?.cancel()
        val nextPage = if(reset || retryPlatform != null) 1 else page + 1
        val targets = if(retryPlatform != null) listOf(retryPlatform) else if(reset) platforms else activePlatforms
        if(reset && retryPlatform == null) { results = emptyList(); progress = emptyMap(); activeQuery = entered; activePlatforms = targets }
        searched = true; loading = true
        currentJob = scope.launch {
            var more = false
            try {
                if(reset && retryPlatform == null && app.store.bool("search.recordHistory", true)) app.store.update { state -> state.copy(searches = (listOf(SearchRecord(entered, targets, sort)) + state.searches.filterNot { it.query == entered && it.savedName.isEmpty() }).take(app.store.number("search.historyLimit", 200))) }
                coroutineScope {
                    targets.map { platform -> launch {
                        progress = progress + (platform to "搜索中")
                        try {
                            var count = 0
                            for((index, expression) in combinations.withIndex()) {
                                progress = progress + (platform to "${index + 1} / ${combinations.size}")
                                val input = expression.keyword(platform) + if(platform == Platform.nhentai && language.isNotBlank()) " language:$language" else ""
                                val batch = withContext(Dispatchers.IO) {
                                    val keyword = if(app.store.bool("search.translate", true) && platform in listOf(Platform.nhentai, Platform.eHentai)) app.repository.tags.searchKeyword(input) else input
                                    app.repository.source(platform).search(keyword, nextPage, sort)
                                }
                                count += batch.size
                                if(batch.isNotEmpty()) more = true
                                results = (results + batch).distinctBy { it.key }
                            }
                            progress = progress + (platform to "完成 · $count 项")
                        } catch(e: CancellationException) { throw e } catch(e: Exception) { progress = progress + (platform to "失败：${e.message}") }
                    } }.joinAll()
                }
                page = nextPage; hasMore = more
            } finally { loading = false }
        }
    }
    LaunchedEffect(route.instance) { if(route.parameter.isNotBlank() && !searched) search() }
    var field by remember { mutableStateOf(TextFieldValue(query)) }
    var searchActive by rememberSaveable { mutableStateOf(app.store.bool("search.focus")) }
    LaunchedEffect(query) { if(field.text != query) field = TextFieldValue(query, TextRange(query.length)) }
    ComicResultsSurface("搜索", results, root = route.parameter.isBlank(),
        titleContent = { SearchTargetMenu(platforms) { platforms = it; if(searched) search() } }, actions = {
            IconAction("line.3.horizontal.decrease.circle", "高级筛选") { showOptions = true }
            ActionMenu(listOf(
                MenuAction("重新搜索", "arrow.clockwise", enabled = query.isNotBlank() && !loading, onClick = { search() }),
                MenuAction("多源版本", "square.stack", enabled = results.isNotEmpty(), onClick = { app.navigator.open(Route("versions", queue = results)) }),
                MenuAction("常用搜索", "star", onClick = { showSaved = true }),
                MenuAction("组合预览与进度", "list.bullet.rectangle", onClick = { showProgress = true }),
                MenuAction("搜索设置", "gearshape", onClick = { app.navigator.open(Route("preferences", parameter = "search")) }),
            )) { open -> IconAction("ellipsis", "搜索工具", open) }
        }, header = {
            GlasmioSearchBar(field, { field = it; query = it.text }, searchActive, { searchActive = it },
                onClose = { searchActive = false }, placeholder = "搜索漫画、作者或标签", closeContentDescription = "取消搜索",
                modifier = Modifier.fillMaxWidth(), onSearch = { search() })
            if(suggestions.isNotEmpty()) Group("标签建议") { suggestions.forEach { suggestion ->
                GlasmioListRow(suggestion.title, subtitle = suggestion.query, onClick = {
                    val split = maxOf(query.lastIndexOf('&'), query.lastIndexOf('/'))
                    query = if(split >= 0) query.take(split + 1) + suggestion.query else suggestion.query
                    suggestions = emptyList()
                    if(app.store.setting("search.suggestionAction", "fill") == "search") search()
                })
            } }
            if(!searched) {
                Group("搜索提示", "支持 / 合并、& 组合和括号嵌套；双引号保留原文。") {
                    GlasmioListRow("多平台聚合搜索", subtitle = "选择多个来源，一次查看搜索结果", systemName = "magnifyingglass")
                }
                Group("搜索历史") {
                    if(data.searches.isEmpty()) EmptyRow("暂无搜索历史", "搜索后会在这里保留关键词和搜索范围。", "clock")
                    data.searches.filter { it.savedName.isEmpty() }.take(20).forEach { record -> GlasmioListRow(record.query, subtitle = record.platforms.joinToString("、") { it.title }, onClick = { query = record.query; platforms = record.platforms; sort = record.sort; search() }) }
                    if(data.searches.any { it.savedName.isEmpty() }) GlasmioListRow("清空搜索历史", titleColor = GlasmioTheme.colors.destructive, onClick = { app.store.update { it.copy(searches = it.searches.filter { record -> record.savedName.isNotEmpty() }) } })
                }
            } else GlasmioListRow("已获取 ${results.size} 项结果", subtitle = if(loading) "正在搜索…" else null, onClick = { showProgress = true }, systemName = "list.bullet.rectangle")
        }, footer = {
            if(loading) Busy()
            else if(searched && results.isEmpty()) EmptyPage("暂无结果", "尝试其他关键词或搜索来源", "magnifyingglass")
            else if(searched && hasMore) GlasmioButton({ search(reset = false) }, Modifier.fillMaxWidth()) { Text("加载更多") }
        })
    SearchOptionsSheet(showOptions, platforms, sort, language, { sort = it }, { language = it }) { showOptions = false }
    SavedSearchesSheet(showSaved, { showSaved = false }, { if(query.isNotBlank()) showSaveName = true }) { record ->
        query = record.query; platforms = record.platforms; sort = record.sort; search()
    }
    AppSheet(showProgress, "组合预览与进度", { showProgress = false }) {
        val clauses = runCatching { SearchExpression.clauses(query) }.getOrDefault(emptyList())
        item { Group("搜索组合") {
            if(clauses.isEmpty()) EmptyRow("暂无搜索组合", "输入关键词后查看组合预览。", "magnifyingglass")
            clauses.forEach { clause -> GlasmioListRow(clause.terms.joinToString(" & ")) }
        } }
        item { Group("逐平台进度") {
            progress.forEach { (platform, status) -> GlasmioListRow(platform.title, subtitle = status,
                onClick = if(status.startsWith("失败")) ({ search(reset = false, retryPlatform = platform) }) else null) }
        } }
    }
    if(showSaveName) TextPrompt("保存常用搜索", "名称", initial = query, dismiss = { showSaveName = false }) { name -> app.store.update { it.copy(searches = listOf(SearchRecord(query, platforms, sort, name)) + it.searches) } }
    ErrorDialog(error) { error = null }
}

