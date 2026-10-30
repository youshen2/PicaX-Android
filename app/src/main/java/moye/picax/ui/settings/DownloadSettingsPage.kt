package moye.picax.ui.settings

import androidx.compose.runtime.Composable
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun DownloadSettingsPage() {
    val app = LocalApp.current
    Page("下载") {
        item { Group("并发与重试") {
            NumberSetting("并发漫画数", "downloads.concurrency", 2, 1..4)
            NumberSetting("并发图片数", "downloads.imageConcurrency", 3, 1..8)
            NumberSetting("失败重试次数", "downloads.retries", 2, 0..5)
            SwitchSetting("使用图片缓存", "downloads.cache", true)
        } }
        item { Group("内容") {
            SwitchSetting("下载章节评论", "downloads.comments", false)
            SwitchSetting("记录本地阅读历史", "downloads.recordHistory", true)
            TextSetting("章节标题屏蔽词（每行一个）", "downloads.blockChapters")
            TextSetting("导出文件名模板", "downloads.nameTemplate", "{title}")
        } }
        item { Group("速度与通知") {
            SwitchSetting("下载限速", "downloads.limit", false)
            NumberSetting("速度上限（KiB/s）", "downloads.kib", 2048, 64..102400)
            SwitchSetting("显示进度通知", "downloads.notifications", true)
            NumberSetting("通知更新间隔（秒）", "downloads.notificationSeconds", 2, 1..30)
        } }
        item { Group("导入") { NumberSetting("PDF 导入宽度", "import.width", 1440, 720..2880) } }
        item { Group { GlasmioListRow("管理下载", onClick = { app.navigator.open(Route("downloads")) }) } }
    }
}
