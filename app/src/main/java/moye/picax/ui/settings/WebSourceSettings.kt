package moye.picax.ui.settings

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.data.source.*
import moye.picax.model.Platform
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable internal fun EhSettings() {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    val tools = remember { SourceTools(app.repository) }
    val selected = preferences["frontend.eHentai"] ?: Platform.eHentai.website
    Group("E-Hentai") {
        ChoiceSetting("画廊站点", "frontend.eHentai", Platform.eHentai.website, tools.ehRoutes.map { it.id to it.title })
        RouteSpeedControl("测试画廊站点", Platform.eHentai, tools.ehRoutes, selected) { app.store.set("frontend.eHentai", it) }
        SwitchSetting("优先加载原图", "eh.original", false)
        SwitchSetting("忽略警告", "eh.ignoreWarning", true)
        SwitchSetting("优先显示副标题", "eh.japaneseTitle", false)
        GlasmioListRow("Profile", onClick = { app.navigator.open(Route("ehProfiles", platform = Platform.eHentai)) })
        ClearWebLoginRow(Platform.eHentai)
    }
}

@Composable internal fun HitomiSettings() {
    val app = LocalApp.current
    Group("Hitomi", "用于缩略图、索引和原图资源。只填写域名即可。") {
        SourceField("图片数据域名", "hitomi.domain", SourceSettings.hitomiDomain, domainOnly = true)
        GlasmioListRow("恢复默认域名", onClick = { app.store.set("hitomi.domain", SourceSettings.hitomiDomain) })
    }
}

@Composable internal fun HtSettings() {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    val tools = remember { SourceTools(app.repository) }
    var routes by remember { mutableStateOf(emptyList<SourceRoute>()) }
    var showOptions by remember { mutableStateOf(false) }
    Group("绅士漫画", "API 地址会影响绅士漫画的列表、详情和图片解析。") {
        SourceField("API 地址", "frontend.htManga", Platform.htManga.website)
        SourceAction("更新 API 分流") { routes = tools.htRoutes(); showOptions = true; null }
        var results by remember { mutableStateOf(emptyList<RouteTiming>()) }
        SourceAction("测试 API 分流") { routes = tools.htRoutes(); results = tools.test(Platform.htManga, routes); null }
        results.forEach { result ->
            GlasmioListRow(result.route.title, detail = result.milliseconds?.let { "$it ms" } ?: result.error, enabled = result.milliseconds != null,
                onClick = { app.store.set("frontend.htManga", result.route.url) }, trailing = {
                    if (preferences["frontend.htManga"] == result.route.url) SfIcon("checkmark", "已选择")
                })
        }
    }
    AppSheet(showOptions, "API 分流", { showOptions = false }) {
        items(routes, key = { it.id }) { route -> GlasmioListRow(route.title, onClick = { app.store.set("frontend.htManga", route.url); showOptions = false }) }
    }
}

@Composable fun EhProfilesPage() {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    var profiles by remember { mutableStateOf(emptyList<EhProfile>()) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(revision) {
        loading = true; error = null
        try { profiles = SourceTools(app.repository).ehProfiles() }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    Page("Profile") {
        if (loading) item { Busy() }
        else if (error != null) item { EmptyPage("加载失败", error.orEmpty(), action = "重试") { revision++ } }
        else item { Group { profiles.forEach { profile ->
            GlasmioListRow(profile.title, onClick = { app.store.set("eh.profile", profile.id) }, trailing = {
                if (preferences["eh.profile"].orEmpty() == profile.id) SfIcon("checkmark", "已选择")
            })
        } } }
    }
}
