package moye.picax.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import moye.picax.data.source.*
import moye.picax.model.Platform
import moye.picax.ui.LocalApp
import moye.picax.ui.components.*

@Composable internal fun JmSettings() {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    val tools = remember { SourceTools(app.repository) }
    val routes = tools.jmRoutes()
    val auto = preferences["jm.autoSelect"] != "false"
    Group("JMComic", "API 域名用于登录和数据请求；图片分流用于封面、头像和阅读图片。") {
        SwitchSetting("自动选择域名", "jm.autoSelect", true)
        ChoiceMenuRow("API 域名", if (auto) "auto" else preferences["jm.apiIndex"] ?: "0", listOf("auto" to "自动") + routes.map { it.id to it.title }, {
            app.store.set("jm.autoSelect", it == "auto")
            if (it != "auto") app.store.set("jm.apiIndex", it)
        }, enabled = !auto)
        if (!preferences["jm.apis"].isNullOrBlank()) Text(preferences["jm.apis"].orEmpty(), Modifier.padding(16.dp), color = GlasmioTheme.colors.secondaryContent)
        SourceAction("更新 API 域名") { val urls = tools.updateJmDomains(); "已获取 ${urls.size} 个 API 域名" }
        RouteSpeedControl("测试 API 域名", Platform.jmComic, routes, if (auto) null else preferences["jm.apiIndex"] ?: "0") {
            app.store.set("jm.autoSelect", false); app.store.set("jm.apiIndex", it)
        }
        ChoiceSetting("图片分流", "jm.imageIndex", "1", tools.imageRoutes().map { it.id to it.title })
        if (preferences["jm.imageIndex"] == "4") SourceField("图片地址", "jm.images", SourceSettings.jmImages[1])
        RouteSpeedControl("测试图片分流", Platform.jmComic, tools.imageRoutes(), preferences["jm.imageIndex"] ?: "1", images = true) { app.store.set("jm.imageIndex", it) }
        ChoiceSetting("收藏排序", "jm.favoriteSort", "latest", listOf("latest" to "最新收藏", "updated" to "最新更新"))
        SourceField("App 版本", "jm.version", "2.1.4", normalize = false)
        SourceAction("更新 App 版本") { "当前 App 版本：${tools.updateJmVersion()}" }
    }
}
