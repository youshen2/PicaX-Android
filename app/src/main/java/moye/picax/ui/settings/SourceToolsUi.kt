package moye.picax.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.data.source.*
import moye.picax.data.WebLoginState
import moye.picax.model.Platform
import moye.picax.ui.LocalApp
import moye.picax.ui.components.*

@Composable internal fun SourceField(title: String, key: String, default: String, domainOnly: Boolean = false, normalize: Boolean = true) {
    val store = LocalApp.current.store
    val preferences by store.preferences.collectAsState()
    val value = preferences[key] ?: default
    fun commit() { if (normalize) store.set(key, if (domainOnly) SourceSettings.domain(store.setting(key, default)) else SourceSettings.baseURL(store.setting(key, default), default)) }
    val latestCommit by rememberUpdatedState(::commit)
    DisposableEffect(key) { onDispose { latestCommit() } }
    Column {
        Text(title, Modifier.padding(start = 16.dp, top = 10.dp), color = GlasmioTheme.colors.secondaryContent)
        GlasmioTextField(value, { store.set(key, it) }, Modifier.onFocusChanged { if (!it.isFocused) commit() }, placeholder = default,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = if (normalize) KeyboardType.Uri else KeyboardType.Text),
            keyboardActions = KeyboardActions(onDone = { commit() }))
    }
}

@Composable internal fun SourceAction(title: String, enabled: Boolean = true, action: suspend () -> String?) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    GlasmioListRow(if (busy) "正在处理…" else title, enabled = enabled && !busy, showDisclosure = false, onClick = {
        scope.launch {
            busy = true
            try { message = action() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.message ?: "操作失败" }
            finally { busy = false }
        }
    })
    if (message != null) GlasmioAlertDialog({ message = null }, title, message, buttons = listOf(GlasmioAlertButtonSpec("好", { message = null })))
}

@Composable internal fun RouteSpeedControl(title: String, platform: Platform, routes: List<SourceRoute>, selected: String?, images: Boolean = false, select: (String) -> Unit) {
    val app = LocalApp.current
    var results by remember { mutableStateOf(emptyList<RouteTiming>()) }
    SourceAction(title) { results = SourceTools(app.repository).test(platform, routes, images); null }
    val fastest = results.mapNotNull { it.milliseconds }.minOrNull()
    results.forEach { result ->
        GlasmioListRow(result.route.title, subtitle = result.route.url.substringAfter("://"), detail = result.milliseconds?.let { "$it ms" } ?: result.error,
            enabled = result.milliseconds != null, showDisclosure = false, onClick = { select(result.route.id) }, trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (result.milliseconds != null && result.milliseconds == fastest) SfIcon("bolt.fill", "最快", tint = GlasmioTheme.colors.accent)
                    if (selected == result.route.id) SfIcon("checkmark", "已选择", tint = GlasmioTheme.colors.accent)
                }
            })
    }
    if (results.isNotEmpty()) Text("使用真实 HTTP 请求测量（非 Ping），数值越低通常越快；结果会受当前网络波动影响。", Modifier.padding(16.dp), color = GlasmioTheme.colors.secondaryContent)
}

@Composable internal fun ClearWebLoginRow(platform: Platform) {
    val app = LocalApp.current
    SourceAction("清除网页登录状态") { WebLoginState.clear(app.store, platform); "网页登录状态已清除" }
}

@Composable internal fun CheckInSection(platform: Platform, signedIn: Boolean) {
    val app = LocalApp.current
    Group("签到") {
        SwitchSetting("每日自动签到", "jm.autoCheckIn", false)
        SourceAction("立即签到", signedIn) { app.repository.source(platform).checkIn() }
    }
}
