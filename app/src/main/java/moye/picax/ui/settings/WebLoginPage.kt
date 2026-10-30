package moye.picax.ui.settings

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import moye.picax.data.WebLoginState
import android.webkit.HttpAuthHandler
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable fun WebLoginPage(platform: Platform) {
    val app = LocalApp.current
    var browser by remember { mutableStateOf<WebView?>(null) }
    val base = app.repository.http.base(platform)
    val login = when(platform) {
        Platform.nhentai -> "$base/login/?next=/"
        Platform.eHentai -> "https://forums.e-hentai.org/index.php?act=Login&CODE=00"
        Platform.htManga -> "$base/users-login.html"
        else -> base
    }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        GlasmioTopBar(platform.title, navigation = { IconAction("chevron.left", "返回", app.navigator::back) }, actions = {
            IconAction("checkmark", "保存登录状态") {
                val manager = CookieManager.getInstance()
                val cookies = WebLoginState.urls(app.store, platform).flatMap { manager.getCookie(it).orEmpty().split(';') }
                    .map { it.trim() }.filter { '=' in it }.distinctBy { it.substringBefore('=') }.joinToString("; ")
                if(cookies.isEmpty()) message = "当前页面还没有登录 Cookie"
                else {
                    val token = cookies.split(';').firstOrNull { it.trim().startsWith("access_token=") }?.trim()?.substringAfter('=').orEmpty()
                    manager.flush()
                    val name = app.store.data.value.accounts.firstOrNull { it.platform == platform }?.username ?: platform.title
                    app.store.saveAccount(platform, name, Credential(token = token, cookie = cookies, userAgent = browser?.settings?.userAgentString.orEmpty(), baseUrl = base))
                    app.navigator.back()
                }
            }
        })
        if (progress in 0..99) androidx.compose.material3.LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            GlasmioButton({ if (browser?.canGoBack() == true) browser?.goBack() }) { Text("后退") }
            GlasmioButton({ if (browser?.canGoForward() == true) browser?.goForward() }) { Text("前进") }
            GlasmioButton({ browser?.reload() }) { Text("刷新") }
        }
        AndroidView(factory = { context -> WebView(context).apply {
            browser = this
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
            }
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) message = error.description.toString()
                }
                override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                    if(app.store.setting("network.mode", "direct") == "proxy" && host == app.store.setting("network.host", "127.0.0.1"))
                        handler.proceed(app.store.setting("network.username"), app.store.vault.get(Platform.local).password)
                    else handler.cancel()
                }
            }
            val credential = app.store.vault.get(platform)
            if(credential.userAgent.isNotBlank()) settings.userAgentString = credential.userAgent
            credential.cookie.split(';').filter { it.contains('=') }.forEach { CookieManager.getInstance().setCookie(base, it.trim()) }
            val executor = ContextCompat.getMainExecutor(context)
            if(app.store.setting("network.mode", "direct") != "proxy") {
                if(WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) ProxyController.getInstance().clearProxyOverride(executor) { loadUrl(login) }
                else loadUrl(login)
            } else if(WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                val host = app.store.setting("network.host", "127.0.0.1").trim()
                val port = app.store.number("network.port", 7890)
                if(host.isBlank() || port !in 1..65535) message = "请先填写有效的代理地址和端口"
                else {
                    val scheme = if(app.store.setting("network.type", "HTTP") == "SOCKS") "socks" else "http"
                    val proxy = ProxyConfig.Builder().addProxyRule("$scheme://$host:$port").build()
                    ProxyController.getInstance().setProxyOverride(proxy, executor) { loadUrl(login) }
                }
            } else message = "当前系统 WebView 不支持代理设置，请更新 Android System WebView"
        } }, modifier = Modifier.weight(1f))
    }
    DisposableEffect(Unit) { onDispose { browser?.stopLoading(); browser?.destroy() } }
    if(message != null) GlasmioAlertDialog({ message = null }, "登录状态", message, buttons = listOf(GlasmioAlertButtonSpec("好", { message = null })))
}
