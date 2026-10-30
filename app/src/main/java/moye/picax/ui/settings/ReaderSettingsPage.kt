package moye.picax.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import moye.picax.ui.components.*

@Composable
fun ReaderSettingsPage() {
    Page("阅读器") {
        item { Group("阅读方式") {
            ChoiceSetting("阅读模式", "reader.mode", "continuous", listOf("continuous" to "竖向连续滚动", "vertical" to "竖向分页", "ltr" to "从左到右分页", "rtl" to "从右到左分页", "curl" to "仿真翻页"))
            SwitchSetting("整卷连续阅读", "reader.whole", false)
            SwitchSetting("多本连续阅读", "reader.multi", false)
            SwitchSetting("自动连续下一本书", "reader.nextBook", false)
            SwitchSetting("阅读列表到边界时接续", "reader.listAuto", true)
            SwitchSetting("切换漫画显示书名", "reader.bookToast", true)
        } }
        item { Group("自动翻页") {
            NumberSetting("翻页间隔（秒）", "reader.autoSeconds", 6, 1..30)
            NumberSetting("翻页距离（% 屏幕高度）", "reader.autoDistance", 85, 10..120)
            SwitchSetting("平滑持续滚动", "reader.smooth", false)
            SwitchSetting("自动进入下一章", "reader.autoChapter", true)
        } }
        item { Group("点按与控制栏") {
            ChoiceSetting("控制栏切换", "reader.uiToggle", "single", listOf("single" to "单击", "double" to "双击"))
            SwitchSetting("点按翻页", "reader.tap", false)
            NumberSetting("边缘识别范围（%）", "reader.tapEdge", 28, 5..45)
            NumberSetting("点按翻页距离（%）", "reader.tapDistance", 85, 10..120)
            SwitchSetting("反转点按翻页", "reader.tapInverted", false)
            SwitchSetting("隐藏系统状态栏", "reader.hideStatus", false)
            SwitchSetting("保持屏幕常亮", "reader.keepAwake", true)
        } }
        item { Group("缩放") {
            SwitchSetting("双指缩放", "reader.zoomPinch", true)
            SwitchSetting("双击缩放", "reader.zoomDouble", true)
            NumberSetting("双击放大（%）", "reader.doubleScale", 175, 120..500)
            SwitchSetting("长按临时缩放", "reader.zoomLong", true)
            NumberSetting("长按触发（毫秒）", "reader.longDelay", 300, 100..1000)
            NumberSetting("长按放大（%）", "reader.longScale", 175, 120..500)
        } }
        item { Group("页面与加载") {
            SwitchSetting("黑色背景", "reader.dark", true)
            SwitchSetting("降低图片亮度", "reader.dim", false)
            NumberSetting("图片间距", "reader.gap", 0, 0..40)
            NumberSetting("首图顶部留白", "reader.top", 115, 0..300)
            NumberSetting("末图底部留白", "reader.bottom", 0, 0..300)
            NumberSetting("预加载页数", "reader.preload", 3, 0..10)
            SwitchSetting("接近章尾预加载下一章", "reader.nextPreload", false)
            NumberSetting("章尾阈值（页）", "reader.nearEnd", 3, 1..10)
            NumberSetting("分页预加载延迟（毫秒）", "reader.pagedDelay", 1200, 0..5000)
            NumberSetting("图片重试次数", "reader.retry", 2, 0..5)
            NumberSetting("重试间隔（毫秒）", "reader.retryDelay", 1000, 100..10000)
        } }
        item { Group("阅读进度") {
            SwitchSetting("显示阅读进度", "reader.pageNumber", true)
            ChoiceSetting("进度样式", "reader.progressStyle", "circular", listOf("circular" to "圆形", "capsule" to "胶囊"))
            ChoiceSetting("进度位置", "reader.progressPosition", "trailing", listOf("leading" to "左侧", "center" to "居中", "trailing" to "右侧"))
            SwitchSetting("玻璃背景", "reader.progressGlass", false)
            SwitchSetting("显示进度页码", "reader.pageLabel", true)
            SwitchSetting("跟随控制栏隐藏", "reader.progressFollow", false)
            SwitchSetting("点按进度跳转", "reader.progressTap", false)
            NumberSetting("背景不透明度（%）", "reader.progressOpacity", 78, 0..100)
            NumberSetting("底部距离", "reader.progressInset", 0, 0..96)
        } }
        item { Group("时间与电量") {
            SwitchSetting("显示时间与电量", "reader.status", false)
            SwitchSetting("跟随控制栏隐藏", "reader.statusFollow", false)
            SwitchSetting("玻璃背景", "reader.statusGlass", false)
            ChoiceSetting("状态样式", "reader.statusStyle", "compact", listOf("compact" to "紧凑", "stacked" to "分行"))
            ChoiceSetting("状态位置", "reader.statusPosition", "bottomLeading", listOf("topLeading" to "左上", "topTrailing" to "右上", "bottomLeading" to "左下", "bottomTrailing" to "右下"))
            NumberSetting("底部距离", "reader.statusInset", 0, 0..96)
        } }
        item { Group("章尾与本地阅读") {
            SwitchSetting("章尾显示下一章按钮", "reader.endButton", false)
            ChoiceSetting("章尾按钮位置", "reader.endPosition", "bottomTrailing", listOf("bottomLeading" to "左下", "bottomTrailing" to "右下", "topLeading" to "左上", "topTrailing" to "右上"))
            NumberSetting("章尾按钮水平边距", "reader.endHorizontal", 20, 0..120)
            NumberSetting("章尾按钮垂直边距", "reader.endVertical", 24, 0..180)
            SwitchSetting("章尾显示评论", "reader.comments", false)
            SwitchSetting("章尾按钮可进入下一本书", "reader.endNextBook", false)
            SwitchSetting("单本阅读退出后删除本地下载", "reader.burnSingle", false)
            SwitchSetting("阅读列表退出后删除已读本地下载", "reader.burnList", false)
        } }
    }
}
