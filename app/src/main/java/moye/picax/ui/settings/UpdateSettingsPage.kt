package moye.picax.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.data.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable fun UpdateSettingsPage() {
    val app = LocalApp.current; val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<AppRelease?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    fun open(url: String) { app.store.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Page("版本更新") {
        item { Group("检查版本") {
            GlasmioListRow("当前版本", detail = moye.picax.BuildConfig.VERSION_NAME)
            TextSetting("GitHub 项目", "updates.repository", AppProject.repository)
            SwitchSetting("启动时检查更新", "updates.onLaunch", false)
            GlasmioListRow("检查更新", enabled = !busy, onClick = { scope.launch {
                busy = true
                try { release = AppUpdates.check(app.repository) } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
                finally { busy = false }
            } })
        } }
        if(busy) item { Busy() }
        release?.let { latest ->
            item { Group("最新发布 ${latest.version}", if(latest.apk == null) "此发布没有 Android 安装包，可查看项目发布说明。" else "下载 Android 安装包后由系统安装。") {
                GlasmioListRow("发布说明", onClick = { open(latest.url) })
                latest.apk?.let { apk -> GlasmioListRow("下载 APK", onClick = { open(apk) }) }
                Text(latest.notes, androidx.compose.ui.Modifier.padding(16.dp))
            } }
        }
    }
    ErrorDialog(error) { error = null }
}
