package moye.picax.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable fun SettingsPage() {
    val app = LocalApp.current
    fun open(key: String) = app.navigator.open(Route("preferences", parameter = key))
    Page("设置") {
        item { Group("账号") { GlasmioListRow("平台账号", systemName = "person.2", onClick = { app.navigator.open(Route("accounts")) }) } }
        item { Group("浏览与阅读") {
            listOf(Triple("首页", "house", "home"), Triple("发现页", "safari", "explore"), Triple("搜索", "magnifyingglass", "search"), Triple("漫画列表", "list.bullet.rectangle", "list"), Triple("漫画详情", "info.square", "detail"), Triple("阅读器", "book", "reader")).forEach { (title, icon, key) ->
                GlasmioListRow(title, systemName = icon, onClick = { open(key) })
            }
        } }
        item { Group("内容与数据") {
            GlasmioListRow("下载", systemName = "arrow.down.circle", onClick = { open("downloads") })
            GlasmioListRow("历史记录", systemName = "clock.arrow.circlepath", onClick = { open("history") })
            GlasmioListRow("阅读时长", systemName = "timer", onClick = { open("time") })
            GlasmioListRow("关键词屏蔽", systemName = "eye.slash", onClick = { app.navigator.open(Route("blocking")) })
            GlasmioListRow("存储管理", systemName = "internaldrive", onClick = { app.navigator.open(Route("storage")) })
            GlasmioListRow("备份与恢复", systemName = "tray.full", onClick = { app.navigator.open(Route("backup")) })
        } }
        item { Group("网络与应用") {
            GlasmioListRow("显示", systemName = "paintbrush", onClick = { open("appearance") })
            GlasmioListRow("App 行为", systemName = "gearshape", onClick = { open("behavior") })
            GlasmioListRow("网络与代理", systemName = "network", onClick = { open("network") })
            GlasmioListRow("标签翻译库", systemName = "character.book.closed", onClick = { app.navigator.open(Route("tagDatabase")) })
            GlasmioListRow("版本更新", systemName = "arrow.down.circle", onClick = { app.navigator.open(Route("appUpdates")) })
            GlasmioListRow("关于 PicaX", systemName = "info.circle", onClick = { app.navigator.open(Route("about")) })
        } }
    }
}

@Composable fun PreferencesPage(key: String) {
    val app = LocalApp.current
    if(key == "downloads") { DownloadSettingsPage(); return }
    if(key == "network") { NetworkSettingsPage(); return }
    if(key == "reader") { ReaderSettingsPage(); return }
    if(key in setOf("home", "list", "detail", "search", "explore")) { BrowsingSettingsPage(key); return }
    val title = mapOf("home" to "首页", "explore" to "发现页", "search" to "搜索", "list" to "漫画列表", "detail" to "漫画详情", "reader" to "阅读器", "downloads" to "下载", "history" to "历史记录", "time" to "阅读时长", "appearance" to "显示", "behavior" to "App 行为", "network" to "网络与代理")[key] ?: "设置"
    Page(title) {
        when(key) {
            "history" -> item { Group("阅读历史") {
                GlasmioListRow("查看历史记录", onClick = { app.navigator.open(Route("library", "历史记录", "history")) })
                ConfirmAction("清空阅读历史", "清空全部阅读历史？") { app.store.update { it.copy(history = emptyList()) } }
                ConfirmAction("重置阅读进度", "重置所有漫画的阅读页码？") { app.store.update { it.copy(history = it.history.map { h -> h.copy(page = 0, pageCount = 0) }) } }
            } }
            "time" -> item { Group("阅读统计") {
                NumberSetting("最低记录时长（秒）", "time.minimum", 5, 0..300)
                GlasmioListRow("查看阅读统计", onClick = { app.navigator.open(Route("statistics")) })
                ConfirmAction("清空阅读时长", "清空全部阅读统计？") { app.store.update { it.copy(readingTime = emptyList()) } }
            } }
            "appearance" -> item { Group("外观") {
                ChoiceSetting("主题", "appearance", "system", listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"))
            } }
            "behavior" -> {
                item { Group("应用保护", "开启后使用设备生物识别或系统密码解锁。") { AppLockSetting() } }
                item { Group("剪贴板") {
                    SwitchSetting("检查漫画链接", "behavior.clipboard", false)
                    SwitchSetting("仅在启动时检查", "behavior.clipboardLaunchOnly", false)
                } }
            }

        }
    }
}

@Composable fun SwitchSetting(title: String, key: String, default: Boolean) {
    val store = LocalApp.current.store
    val preferences by store.preferences.collectAsState()
    GlasmioListRow(title, trailing = { GlasmioSwitch(preferences[key]?.toBoolean() ?: default, { store.set(key, it) }) })
}
@Composable fun ChoiceSetting(title: String, key: String, default: String, options: List<Pair<String, String>>) {
    val store = LocalApp.current.store
    val preferences by store.preferences.collectAsState()
    ChoiceMenuRow(title, preferences[key] ?: default, options, { store.set(key, it) })
}
@Composable fun TextSetting(title: String, key: String, default: String = "") {
    val store = LocalApp.current.store
    val preferences by store.preferences.collectAsState()
    var show by remember { mutableStateOf(false) }
    GlasmioListRow(title, detail = (preferences[key] ?: default).ifBlank { "未设置" }, onClick = { show = true })
    if(show) TextPrompt(title, initial = store.setting(key, default), dismiss = { show = false }) { store.set(key, it) }
}
@Composable fun NumberSetting(title: String, key: String, default: Int, range: IntRange) {
    val store = LocalApp.current.store
    val preferences by store.preferences.collectAsState()
    var show by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    GlasmioListRow(title, detail = (preferences[key]?.toIntOrNull() ?: default).toString(), onClick = { text = store.number(key, default).toString(); show = true })
    if(show) GlasmioAlertDialog({ show = false }, title, "范围 ${range.first}–${range.last}", buttons = listOf(
        GlasmioAlertButtonSpec("取消", { show = false }, GlasmioAlertButtonRole.Cancel),
        GlasmioAlertButtonSpec("保存", { store.set(key, text.toInt()); show = false }, enabled = text.toIntOrNull()?.let { it in range } == true)
    )) { GlasmioTextField(text, { text = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
}
@Composable fun ConfirmAction(title: String, message: String, action: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    GlasmioListRow(title, titleColor = GlasmioThemeColors.destructive(), onClick = { show = true })
    if(show) GlasmioAlertDialog({ show = false }, message, buttons = listOf(GlasmioAlertButtonSpec("取消", { show = false }, GlasmioAlertButtonRole.Cancel), GlasmioAlertButtonSpec("确定", { action(); show = false }, GlasmioAlertButtonRole.Destructive)))
}
private object GlasmioThemeColors { @Composable fun destructive() = io.github.neoruaa.glasmio.core.GlasmioTheme.colors.destructive }
