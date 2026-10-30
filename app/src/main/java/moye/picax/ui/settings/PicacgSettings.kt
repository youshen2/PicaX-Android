package moye.picax.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import kotlinx.coroutines.*
import moye.picax.data.source.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable internal fun PicacgSettings(signedIn: Boolean) {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    val tools = remember { SourceTools(app.repository) }
    Group("PicACG") {
        ChoiceSetting("分流", "picacg.channel", "3", (1..3).map { "$it" to "分流 $it" })
        RouteSpeedControl("测试 API 分流", Platform.picacg, tools.picacgRoutes, preferences["picacg.channel"] ?: "3") { app.store.set("picacg.channel", it) }
        ChoiceSetting("图片质量", "picacg.quality", "medium", listOf("low" to "省流", "medium" to "均衡", "high" to "高清", "original" to "原图"))
        ChoiceSetting("搜索与分类排序", "picacg.sort", "dd", listOf("dd" to "新到旧", "da" to "旧到新", "ld" to "最多喜欢", "vd" to "最多指名"))
        ChoiceSetting("收藏排序", "picacg.favoriteSort", "da", listOf("da" to "旧到新", "dd" to "新到旧"))
        SwitchSetting("显示头像框", "picacg.avatarFrame", true)
        SwitchSetting("自动打卡", "picacg.autoCheckIn", false)
        SourceAction("立即打卡", signedIn) { app.repository.source(Platform.picacg).checkIn() }
        GlasmioListRow("我的评论", enabled = signedIn, systemName = "text.bubble", onClick = { app.navigator.open(Route("ownComments", platform = Platform.picacg)) })
    }
}

@Composable internal fun PicacgProfileSection(signedIn: Boolean) {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    var profile by remember { mutableStateOf<Uploader?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(signedIn, revision) {
        profile = null; error = null
        if (signedIn) {
            loading = true
            try { profile = (app.repository.source(Platform.picacg) as PicacgSource).profile() }
            catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
            finally { loading = false }
        }
    }
    Group("资料") {
        val user = profile
        if (user != null) Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                AsyncImage(user.avatar, "头像", Modifier.size(48.dp).clip(CircleShape))
                if (preferences["picacg.avatarFrame"] != "false" && user.avatarFrame.isNotBlank()) AsyncImage(user.avatarFrame, null, Modifier.fillMaxSize())
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.name, fontWeight = FontWeight.SemiBold)
                Text("Lv.${user.level} · ${user.experience} EXP", color = GlasmioTheme.colors.secondaryContent)
                if (user.slogan.isNotBlank()) Text(user.slogan, color = GlasmioTheme.colors.secondaryContent)
            }
        } else Text(when { loading -> "正在加载资料"; error != null -> error!!; signedIn -> "尚未加载资料。"; else -> "登录后显示 PicACG 资料。" }, Modifier.padding(16.dp), color = if (error != null) GlasmioTheme.colors.destructive else GlasmioTheme.colors.secondaryContent)
        GlasmioListRow("刷新资料", enabled = signedIn && !loading, onClick = { revision++ })
    }
}
