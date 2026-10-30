package moye.picax.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*
import java.text.DateFormat
import java.util.Date

@Composable fun AccountsPage() {
    val app = LocalApp.current
    val data = appData()
    Page("平台账号") {
        item { Group("平台") { Platform.online.forEach { platform ->
            val account = data.accounts.firstOrNull { it.platform == platform }
            GlasmioListRow(platform.title, systemName = platform.symbol, trailing = {
                if (account != null) SfIcon("checkmark", "已登录，${account.username}", tint = GlasmioTheme.colors.accent)
            }, onClick = { app.navigator.open(Route("account", platform = platform)) })
        } } }
    }
}

@Composable fun AccountPage(platform: Platform) {
    val app = LocalApp.current
    val data = appData()
    val account = data.accounts.firstOrNull { it.platform == platform }
    val credential = app.store.vault.get(platform)
    var username by rememberSaveable(platform) { mutableStateOf(account?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val supportsPassword = platform in listOf(Platform.picacg, Platform.jmComic, Platform.htManga)
    Page(platform.title) {
        if (supportsPassword) item { Group("登录信息", "应用会保存必要的登录信息，用来下次继续使用。") {
            GlasmioTextField(username, { username = it }, placeholder = "用户名", enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false))
            GlasmioTextField(password, { password = it }, placeholder = "密码", enabled = !busy, visualTransformation = PasswordVisualTransformation())
            error?.let { Text(it, Modifier.padding(16.dp), color = GlasmioTheme.colors.destructive) }
        } }
        if (account != null) item { Group("当前状态") {
            GlasmioListRow("账号", detail = account.username)
            GlasmioListRow("登录状态", detail = if (credential.token.isNotEmpty() || credential.cookie.isNotEmpty() || credential.password.isNotEmpty()) "已保存" else "未保存")
            GlasmioListRow("登录时间", detail = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(account.updatedAt)))
        } }
        item { Group { GlasmioListRow("漫画源设置", systemName = "slider.horizontal.3", onClick = {
            app.navigator.open(Route("source", parameter = "account", platform = platform))
        }) } }
        item { Group {
            if (supportsPassword) GlasmioListRow(if (busy) "正在验证" else if (account == null) "登录" else "重新登录",
                enabled = !busy && username.isNotBlank() && password.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true; error = null
                        try {
                            val result = app.repository.source(platform).login(username.trim(), password)
                            app.store.saveAccount(platform, username.trim(), result)
                            password = ""
                            app.navigator.back()
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "登录失败" }
                        finally { busy = false }
                    }
                })
            if (platform !in listOf(Platform.picacg, Platform.jmComic)) GlasmioListRow("通过网页登录", systemName = "safari", enabled = !busy,
                onClick = { app.navigator.open(Route("web", platform = platform)) })
            if (account != null) GlasmioListRow("退出登录", enabled = !busy, titleColor = GlasmioTheme.colors.destructive, onClick = {
                app.store.logout(platform); username = ""; password = ""; error = null
            })
        } }
    }
}
