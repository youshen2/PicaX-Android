package moye.picax.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.ui.*
import moye.picax.R
import moye.picax.ui.*
import moye.picax.ui.components.*
import moye.picax.ui.settings.LegalContent

@Composable fun AgePage() {
    val app = LocalApp.current
    var underage by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(GlasmioTheme.colors.elevatedBackground).safeDrawingPadding().padding(horizontal = 28.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 72.dp)) {
            Text("18+", color = Color(0xFFFF3B30), fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Text("年龄确认", fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Spacer(Modifier.height(12.dp))
            Text("PicaX 仅面向已满 18 周岁的用户。应用可能展示来自第三方平台的成人题材内容。", fontSize = 17.sp, color = GlasmioTheme.colors.secondaryContent, lineHeight = 25.sp)
        }
        GlasmioButton({ app.store.set("adult", true) }, Modifier.fillMaxWidth(), emphasis = GlassEmphasis.Prominent, size = GlasmioButtonSize.Large) { Text("我已满 18 周岁", fontWeight = FontWeight.SemiBold) }
        GlasmioListRow("我未满 18 周岁", onClick = { underage = true }, titleAlignment = androidx.compose.ui.text.style.TextAlign.Center, showDisclosure = false, showTopSeparator = false)
        Spacer(Modifier.height(16.dp))
    }
    if(underage) GlasmioAlertDialog({ underage = false }, "无法继续", "PicaX 仅向已满 18 周岁的用户开放。请关闭应用，并在满足年龄要求后再使用。", buttons = listOf(GlasmioAlertButtonSpec("知道了", { underage = false })))
}

@Composable fun OnboardingPage() {
    val app = LocalApp.current
    var stage by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(GlasmioTheme.colors.elevatedBackground).safeDrawingPadding().padding(30.dp)) {
        if(stage == 0) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(38.dp))
                Image(painterResource(R.drawable.app_logo), "PicaX", Modifier.size(84.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
                Spacer(Modifier.height(44.dp))
                Text("PicaX", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("把阅读、收藏和发现放在一个更顺手的阅读入口里。", fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = GlasmioTheme.colors.secondaryContent)
                Spacer(Modifier.height(30.dp))
                GlasmioListRow("原生界面", subtitle = "Android 原生体验，Glasmio 液态玻璃", systemName = "books.vertical", showTopSeparator = false)
                GlasmioListRow("第三方内容", subtitle = "漫画与图片来自所连接的第三方平台", systemName = "network", showTopSeparator = false)
                Spacer(Modifier.height(30.dp))
                Text("接下来需要确认用户协议与免责声明", fontSize = 13.sp, color = GlasmioTheme.colors.secondaryContent)
            }
        } else {
            Text(if(stage == 1) "用户协议" else "免责声明", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            LegalContent(if(stage == 1) "agreement" else "disclaimer", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        GlasmioButton({ if(stage < 2) stage++ else { app.store.set("onboarding", true) } }, Modifier.fillMaxWidth(), emphasis = GlassEmphasis.Prominent, size = GlasmioButtonSize.Large) {
            Text(if(stage == 0) "继续" else "我已阅读并同意", fontWeight = FontWeight.SemiBold)
        }
        if(stage > 0) GlasmioListRow("返回", onClick = { stage-- }, showDisclosure = false, showTopSeparator = false, titleAlignment = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
