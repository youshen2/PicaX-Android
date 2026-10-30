package moye.picax.ui.settings

import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.Platform
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun NetworkSettingsPage() {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    var password by remember { mutableStateOf("") }
    var passwordSheet by remember { mutableStateOf(false) }
    Page("网络与代理") {
        item { Group("连接方式") {
            ChoiceMenuRow("网络线路", if(preferences["network.mode"] == "proxy") "proxy" else "direct", listOf("direct" to "直连", "proxy" to "代理服务器"), { app.store.set("network.mode", it) })
        } }
        item { Group("代理服务器") {
            ChoiceSetting("代理协议", "network.type", "HTTP", listOf("HTTP" to "HTTP", "SOCKS" to "SOCKS5"))
            TextSetting("服务器地址", "network.host", "127.0.0.1")
            NumberSetting("端口", "network.port", 7890, 1..65535)
            TextSetting("用户名", "network.username")
            GlasmioListRow("代理密码", detail = "加密保存", onClick = { password = ""; passwordSheet = true })
        } }
    }
    if(passwordSheet) GlasmioAlertDialog({ passwordSheet = false }, "代理凭证", buttons = listOf(
        GlasmioAlertButtonSpec("取消", { passwordSheet = false }),
        GlasmioAlertButtonSpec("保存", {
            app.store.vault.save(Platform.local, app.store.vault.get(Platform.local).copy(password = password))
            passwordSheet = false
        })
    )) { GlasmioTextField(password, { password = it }, visualTransformation = PasswordVisualTransformation()) }
}
