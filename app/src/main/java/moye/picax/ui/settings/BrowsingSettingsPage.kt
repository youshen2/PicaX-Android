package moye.picax.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun BrowsingSettingsPage(key: String) {
    val app = LocalApp.current; val preferences by app.store.preferences.collectAsState()
    val titles = mapOf("home" to "首页", "list" to "漫画列表", "detail" to "漫画详情", "search" to "搜索", "explore" to "发现页")
    Page(titles.getValue(key)) {
        when(key) {
            "home" -> {
                item { Group("详细内容") {
                    SwitchSetting("账号管理入口", "home.accounts", true)
                    SwitchSetting("历史记录", "home.history", true)
                    SwitchSetting("稍后再读", "home.later", true)
                    SwitchSetting("阅读时长", "home.time", true)
                    SwitchSetting("下载", "home.downloads", true)
                } }
                item { Group("卡片数量") {
                    NumberSetting("历史记录", "home.historyLimit", 10, 1..30)
                    NumberSetting("稍后再读", "home.laterLimit", 10, 1..30)
                    NumberSetting("阅读时长", "home.timeLimit", 6, 1..30)
                    NumberSetting("下载", "home.downloadLimit", 8, 1..30)
                } }
                item { OrderPreference("排序", "home.order", homeSectionTitles) }
            }
            "list" -> {
                item { Group("布局") { GlasmioSegmentedControl(listOf("list" to "列表", "waterfall" to "瀑布流"), preferences["list.layout"] ?: "list", { app.store.set("list.layout", it) }, modifier = Modifier.fillMaxWidth()) } }
                item { Group("显示内容") {
                    SwitchSetting("显示阅读进度", "list.progress", true); SwitchSetting("显示收藏状态", "list.favorite", true)
                    SwitchSetting("显示标签", "list.tags", true); NumberSetting("最多显示标签", "list.tagLimit", 5, 1..10)
                    SwitchSetting("显示热度", "list.popularity", true); SwitchSetting("显示页数", "list.pages", true)
                } }
                item { Group("跨平台阅读记录") {
                    SwitchSetting("按名称识别跨平台记录", "list.matchTitles", true)
                    NumberSetting("名称相似度阈值（%）", "list.matchThreshold", 95, 50..100)
                } }
                item { Group("列表隐藏", "收藏夹、历史与本地下载不应用已读和稍后再读过滤。") {
                    SwitchSetting("隐藏已读内容", "list.hideRead", false)
                    NumberSetting("已读隐藏阈值（%）", "list.readThreshold", 100, 0..100)
                    SwitchSetting("隐藏稍后再读内容", "list.hideLater", false)
                } }
            }
            "detail" -> {
                item { Group { SwitchSetting("阅读按钮使用封面颜色", "detail.coverAccent", true) } }
                item { Group("章节") {
                    ChoiceSetting("章节排序", "detail.chapterDescending", "false", listOf("false" to "正序", "true" to "倒序"))
                    SwitchSetting("单独分区显示章节", "detail.chapterSection", false)
                    SwitchSetting("显示标签", "detail.tags", true); SwitchSetting("显示相关推荐", "detail.related", true)
                } }
                item { OrderPreference("内容顺序", "detail.order", detailSectionTitles) }
            }
            "search" -> {
                item { Group("输入与建议") {
                    SwitchSetting("打开时聚焦搜索框", "search.focus", false)
                    SwitchSetting("搜索建议", "search.suggestions", true)
                    SwitchSetting("中文标签转换", "search.translate", true)
                    ChoiceSetting("点击建议", "search.suggestionAction", "fill", listOf("fill" to "填入搜索框", "search" to "直接搜索"))
                } }
                item { Group("默认搜索范围") {
                    ChoiceSetting("默认范围", "search.target", "single", listOf("single" to "单个平台", "aggregate" to "多平台聚合"))
                    ChoiceSetting("默认来源", "search.platform", "picacg", Platform.online.map { it.name to it.title })
                    val selected = (preferences["search.aggregate"] ?: Platform.online.joinToString(",") { it.name }).split(',').toSet()
                    Platform.online.forEach { platform -> GlasmioListRow(platform.title, trailing = { GlasmioSwitch(platform.name in selected, { on ->
                        val next = if(on) selected + platform.name else selected - platform.name
                        if(next.isNotEmpty()) app.store.set("search.aggregate", next.joinToString(","))
                    }) }) }
                } }
                item { Group("历史记录") { SwitchSetting("记录搜索历史", "search.recordHistory", true); NumberSetting("最多保留记录", "search.historyLimit", 200, 10..1000) } }
            }
            "explore" -> item { Group("发现") {
                ChoiceSetting("默认来源", "explore.platform", "picacg", Platform.online.map { it.name to it.title })
                SwitchSetting("分类显示封面", "categories.covers", true)
            } }
        }
    }
}
