package moye.picax.ui.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import moye.picax.ui.*
import moye.picax.ui.components.*
import moye.picax.ui.settings.ChoiceSetting
import moye.picax.ui.settings.NumberSetting
import moye.picax.ui.settings.SwitchSetting

@Composable
fun ReaderOptionsMenu(automatic: Boolean, offline: Boolean, onAutomatic: () -> Unit, onProgress: () -> Unit, onComments: () -> Unit, onDetail: () -> Unit) {
    val app = LocalApp.current
    var advanced by remember { mutableStateOf(false) }
    ActionMenu(listOf(
        MenuAction(if(automatic) "停止自动翻页" else "开始自动翻页", "timer", onClick = onAutomatic),
        MenuAction("翻页选项", "slider.horizontal.3", onClick = { advanced = true }),
        MenuAction("选择阅读进度", "gauge", onClick = onProgress),
        MenuAction("章节评论", "text.bubble", onClick = onComments),
        MenuAction("漫画详情", "info.circle", onClick = onDetail),
        MenuAction("阅读设置", "gearshape", onClick = { app.navigator.open(Route("preferences", parameter = "reader")) }),
    )) { open -> IconAction("ellipsis", "阅读选项", open) }
    AppSheet(advanced, "翻页选项", { advanced = false }) {
        item { Group("自动翻页") {
            NumberSetting("翻页间隔（秒）", "reader.autoSeconds", 6, 1..30)
            NumberSetting("翻页距离（%）", "reader.autoDistance", 85, 10..120)
            SwitchSetting("平滑持续滚动", "reader.smooth", false)
            SwitchSetting("自动进入下一章", "reader.autoChapter", true)
        } }
        item { Group("连续阅读") {
            SwitchSetting("整卷连续阅读", "reader.whole", false)
            SwitchSetting("多本连续阅读", "reader.multi", false)
            SwitchSetting("自动连续下一本书", "reader.nextBook", false)
        } }
        if(offline) item { Group("本地下载") { SwitchSetting("退出后删除本地下载", "reader.burnSingle", false) } }
    }
}
