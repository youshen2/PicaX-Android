package moye.picax

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropProvider
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCanvasBackdrop
import io.github.neoruaa.glasmio.icons.GlasmioIconProvider
import kotlinx.serialization.encodeToString
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.PicaXIcons
import moye.picax.ui.library.DetailPage
import moye.picax.ui.settings.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PlatformPagesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun platformPagesAndReadmeScreenshots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PicaXApplication
        val original = app.store.data.value
        val page = mutableStateOf("login")
        val platform = mutableStateOf(Platform.picacg)
        val navigator = Navigator(app.store, mutableStateListOf(app.store.json.encodeToString(Route("home"))))
        val environment = AppEnvironment(app.store, app.repository, navigator)
        val chapter = Chapter("demo", "第一话 · 出发", pages = 3)
        val comic = Comic("readme-demo", Platform.local, "山间手记", "PicaX 演示文档", tags = listOf("日常", "旅行", "本地漫画"), pages = 3)
        val detail = ComicDetail(comic, "一次出发，一路风景。这个本地演示文档用于展示详情页与阅读器。", listOf(chapter))
        val download = DownloadRecord(detail, chapter, listOf("1", "2", "3"), 3, "已完成")
        app.store.downloadDir(download).mkdirs()
        repeat(3) { index ->
            val bitmap = Bitmap.createBitmap(720, 1080, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(246, 242, 231))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.rgb(69, 90, 77)
            canvas.drawRect(32f, 32f, 688f, 590f, paint)
            paint.color = android.graphics.Color.rgb(149, 178, 154)
            val path = android.graphics.Path().apply { moveTo(32f, 590f); lineTo(200f, 170f); lineTo(398f, 590f); lineTo(530f, 240f); lineTo(688f, 590f); close() }
            canvas.drawPath(path, paint)
            paint.color = android.graphics.Color.rgb(249, 220, 160)
            canvas.drawCircle(534f, 150f, 58f, paint)
            paint.color = android.graphics.Color.rgb(69, 90, 77)
            paint.textSize = 58f
            canvas.drawText("山间手记", 56f, 700f, paint)
            paint.textSize = 26f
            canvas.drawText("沿着山路，慢慢向前。", 56f, 758f, paint)
            paint.textSize = 20f
            canvas.drawText("PicaX 本地演示 · ${index + 1}", 56f, 1015f, paint)
            app.store.pageFile(download, index).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val localComic = comic.copy(cover = android.net.Uri.fromFile(app.store.pageFile(download, 0)).toString())
        val localDownload = download.copy(detail = detail.copy(comic = localComic))
        app.store.update { it.copy(accounts = emptyList(), downloads = listOf(localDownload), preferences = it.preferences + mapOf("reader.mode" to "ltr", "reader.dark" to "false", "reader.status" to "false")) }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalApp provides environment) {
                    GlasmioTheme(isDark = false) {
                        GlasmioIconProvider(PicaXIcons) {
                            GlasmioBackdropProvider(rememberCanvasBackdrop { drawRect(Color(0xfff2f2f7)) }) {
                                Box(Modifier.fillMaxSize().background(Color(0xfff2f2f7))) {
                                    key(page.value, platform.value) {
                                        when (page.value) {
                                            "login" -> AccountPage(platform.value)
                                            "source" -> SourceFeaturesPage(platform.value)
                                            "about" -> AboutPage()
                                            "detail" -> DetailPage(localComic, offline = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            fun capture(name: String) {
                compose.mainClock.advanceTimeBy(1500)
                compose.waitForIdle()
                val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val directory = File(app.filesDir, "readme-screenshots").apply { mkdirs() }
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            compose.onNodeWithText("登录信息").assertExists()
            instrumentation.runOnMainSync {
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
                androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = true
            }
            compose.onNodeWithText("登录", useUnmergedTree = true).assertExists()
            capture("platform-login")
            val targets = mapOf(Platform.picacg to "显示头像框", Platform.jmComic to "自动选择域名", Platform.nhentai to "清除网页登录状态", Platform.eHentai to "画廊站点", Platform.hitomi to "恢复默认域名", Platform.htManga to "更新 API 分流")
            targets.forEach { (source, target) ->
                compose.runOnIdle { platform.value = source; page.value = "source" }
                compose.onNodeWithText(target).performScrollTo().assertIsDisplayed()
                if (source == Platform.picacg) capture("picacg-settings")
                if (source == Platform.jmComic) {
                    compose.onNodeWithText("图片分流").performScrollTo().performClick()
                    capture("source-menu")
                    compose.onNodeWithText("分流 3").performClick()
                }
            }
            compose.runOnIdle { page.value = "about" }
            compose.onNodeWithText("youshen2/PicaX-Android").assertExists()
            compose.onNodeWithText("Swift 原项目").assertDoesNotExist()
            compose.runOnIdle { page.value = "detail" }
            compose.waitUntil(10000) { compose.onAllNodesWithText("阅读").fetchSemanticsNodes().isNotEmpty() }
            capture("comic-detail")
        } finally { app.store.update { original } }
    }
}
