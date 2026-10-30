package moye.picax.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.picax.BuildConfig
import moye.picax.R
import moye.picax.data.AppProject
import moye.picax.ui.*
import moye.picax.ui.components.*
import org.json.JSONObject

@Composable fun AboutPage() {
    val app = LocalApp.current
    fun open(url: String) { app.store.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Page("关于 PicaX") {
        item { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.app_logo), "PicaX", Modifier.size(84.dp))
            Text("PicaX", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("版本 ${BuildConfig.VERSION_NAME}", color = GlasmioTheme.colors.secondaryContent)
            Text("多漫画源阅读客户端", color = GlasmioTheme.colors.secondaryContent)
        } }
        item { Group("项目") {
            GlasmioListRow("项目仓库", subtitle = AppProject.repository, systemName = "chevron.left.forwardslash.chevron.right", onClick = { open(AppProject.url) })
            GlasmioListRow("Glasmio", subtitle = "iOS 风格界面与液态玻璃", systemName = "sparkles", onClick = { open("https://github.com/NEORUAA/Glasmio") })
        } }
        item { Group("法律文档") {
            listOf("agreement" to "用户协议", "privacy" to "隐私政策", "disclaimer" to "免责声明").forEach { (key, title) -> GlasmioListRow(title, systemName = "doc.text", onClick = { app.navigator.open(Route("legal", title, key)) }) }
            GlasmioListRow("Glasmio 开源许可", subtitle = "Mozilla Public License 2.0 · Apache 2.0", onClick = { open("https://github.com/NEORUAA/Glasmio/blob/main/THIRD_PARTY_NOTICES.md") })
        } }
    }
}

@Composable fun LegalPage(key: String) {
    val title = when(key) { "agreement" -> "用户协议"; "privacy" -> "隐私政策"; else -> "免责声明" }
    Page(title) { item { LegalContent(key, scrollable = false) } }
}
@Composable fun LegalContent(key: String, modifier: Modifier = Modifier, scrollable: Boolean = true) {
    val app = LocalApp.current
    var document by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(key) { document = withContext(Dispatchers.IO) { app.store.context.assets.open("$key.json").bufferedReader().use { JSONObject(it.readText()) } } }
    Column(modifier.then(if(scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        document?.let { doc ->
            Text(doc.getString("introduction"), fontSize = 16.sp, lineHeight = 24.sp)
            val sections = doc.getJSONArray("sections")
            for(index in 0 until sections.length()) {
                val section = sections.getJSONObject(index)
                Text(section.getString("title"), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(section.getString("body"), fontSize = 15.sp, lineHeight = 23.sp, color = GlasmioTheme.colors.secondaryContent)
            }
        }
    }
}
