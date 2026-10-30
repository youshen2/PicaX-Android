package moye.picax.ui.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.*
import moye.picax.ui.LocalApp
import moye.picax.ui.appData

private val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
private fun authenticate(activity: FragmentActivity, success: () -> Unit, failure: (String) -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { success() }
        override fun onAuthenticationError(code: Int, message: CharSequence) { failure(message.toString()) }
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("解锁 PicaX").setAllowedAuthenticators(authenticators).build())
}
@Composable fun AppLockSetting() {
    val app = LocalApp.current
    val activity = LocalActivity.current as FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    GlasmioListRow("应用锁", trailing = { GlasmioSwitch(app.store.bool("lock.enabled"), { enabled ->
        if(!enabled) authenticate(activity, { app.store.set("lock.enabled", false) }, { error = it })
        else if(BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS)
            authenticate(activity, { app.store.set("lock.enabled", true) }, { error = it })
        else error = "请先在系统设置中启用设备密码或生物识别"
    }) })
    ChoiceSetting("后台锁定时间", "lock.timeout", "0", listOf("0" to "立即", "60" to "1 分钟", "300" to "5 分钟", "900" to "15 分钟"))
    if(error != null) GlasmioAlertDialog({ error = null }, "应用锁", error, buttons = listOf(GlasmioAlertButtonSpec("好", { error = null })))
}
@Composable fun AppLockOverlay() {
    val app = LocalApp.current
    val data = appData()
    val activity = LocalActivity.current as FragmentActivity
    val owner = LocalLifecycleOwner.current
    val enabled = data.preferences["lock.enabled"] == "true"
    var locked by remember { mutableStateOf(enabled) }
    var obscured by remember { mutableStateOf(false) }
    var backgroundAt by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner, enabled) {
        val observer = LifecycleEventObserver { _, event -> when(event) {
            Lifecycle.Event.ON_STOP -> { obscured = enabled; backgroundAt = android.os.SystemClock.elapsedRealtime() }
            Lifecycle.Event.ON_START -> {
                if(enabled && backgroundAt > 0 && android.os.SystemClock.elapsedRealtime() - backgroundAt >= app.store.number("lock.timeout", 0) * 1000L) locked = true
                obscured = false
            }
            else -> Unit
        } }
        owner.lifecycle.addObserver(observer)
        if(enabled) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else { locked = false; activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if(enabled && (locked || obscured)) Box(Modifier.fillMaxSize().background(GlasmioTheme.colors.elevatedBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("PicaX 已锁定", style = GlasmioTheme.typography.headlineSmall)
            GlasmioButton({ authenticate(activity, { locked = false; error = null }, { error = it }) }) { Text("解锁") }
            if(error != null) Text(error!!)
        }
    }
}
