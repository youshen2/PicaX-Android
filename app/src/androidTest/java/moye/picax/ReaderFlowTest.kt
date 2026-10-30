package moye.picax

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moye.picax.data.DocumentTools
import moye.picax.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ReaderFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun nativeSheetsMenusDetailAndReader() {
        val app = compose.activity.application as PicaXApplication
        compose.runOnIdle { app.store.update { AppData() } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("年龄确认").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("年龄确认").assertIsDisplayed()
        clickText("我已满 18 周岁")
        clickText("继续")
        clickText("我已阅读并同意")
        clickText("我已阅读并同意")
        clickDescription("设置")
        compose.onNodeWithText("阅读器").performScrollTo().performClick()
        settle()
        clickText("阅读模式")
        capture("mode-menu")
        clickText("从左到右分页")
        clickDescription("返回")
        clickDescription("返回")

        val archive = File(app.cacheDir, "layout-fixture.cbz")
        ZipOutputStream(archive.outputStream()).use { zip ->
            for(page in listOf(3, 1, 2)) {
                zip.putNextEntry(ZipEntry("page$page.png"))
                val bitmap = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.rgb(247, 244, 238))
                val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 5f; style = Paint.Style.STROKE }
                canvas.drawRect(24f, 24f, 456f, 330f, ink)
                canvas.drawRect(24f, 354f, 230f, 696f, ink)
                canvas.drawRect(254f, 354f, 456f, 696f, ink)
                ink.style = Paint.Style.FILL; ink.textSize = 36f
                canvas.drawText("PAGE $page", 150f, 190f, ink)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                bitmap.recycle(); zip.closeEntry()
            }
        }
        val imported = runBlocking { DocumentTools(app.store).importComic(Uri.fromFile(archive)) }
        val record = app.store.data.value.downloads.first()
        val comic = imported.copy(title = "排版验收样本", subtitle = "本地测试作者", tags = listOf("artist:sample", "language:chinese", "日常", "冒险"), pages = 5)
        val first = record.copy(detail = record.detail.copy(comic = comic, description = "用于检查详情、菜单与阅读器排版的本地测试文档。"), chapter = record.chapter.copy(title = "第一章"))
        val second = first.copy(chapter = Chapter("second", "第二章", 2, 2), urls = record.urls.take(2), completed = 2)
        app.store.downloadDir(second).mkdirs()
        repeat(2) { app.store.pageFile(first, it).copyTo(app.store.pageFile(second, it), overwrite = true) }
        compose.runOnIdle { app.store.update { it.copy(downloads = listOf(first, second)) } }
        compose.waitForIdle()
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(comic.title))
        compose.onRoot().performTouchInput { swipeUp(startY = centerY, endY = top + 100f) }
        settle()
        clickText(comic.title)
        capture("download-sheet")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("已下载").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("已下载").assertExists()
        clickText("查看详情")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("阅读").fetchSemanticsNodes().isNotEmpty() }
        capture("detail")
        clickDescription("章节")
        compose.onNodeWithText("章节 2").assertIsDisplayed()
        capture("chapter-sheet")
        clickDescription("关闭")
        compose.runOnIdle { app.store.set("detail.chapterSection", true) }
        compose.onNodeWithContentDescription("章节").assertDoesNotExist()
        compose.onNodeWithText("章节 2").assertExists()
        compose.runOnIdle { app.store.set("detail.chapterSection", false) }
        clickDescription("收藏")
        compose.onNodeWithText("本地收藏").assertIsDisplayed()
        capture("favorite-sheet")
        clickText("关闭")
        clickText("阅读")
        compose.waitUntil(10_000) { app.store.data.value.history.any { it.comic.key == comic.key && it.pageCount == 3 } }
        compose.onNodeWithContentDescription("漫画页面").performTouchInput { swipeLeft() }
        settle()
        compose.waitUntil(10_000) { app.store.data.value.history.first { it.comic.key == comic.key }.page == 1 }
        capture("reader")
        clickDescription("阅读选项")
        capture("reader-menu")
        clickText("选择阅读进度")
        compose.onNodeWithText("选择阅读进度").assertExists()
        clickDescription("下一页")
        compose.onNodeWithText("第 3 页").assertExists()
        clickDescription("上一页")
        compose.onNodeWithText("第 2 页").assertExists()
        capture("progress-sheet")
        clickText("取消")
        clickDescription("书签")
        compose.onNodeWithText("当前位置").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("排版检查书签")
        clickText("保存当前位置")
        compose.waitUntil(10_000) { app.store.data.value.bookmarks.isNotEmpty() }
        capture("bookmark-sheet")
        assertEquals(1, app.store.data.value.bookmarks.first().page)
        clickText("完成")
        clickDescription("返回")
        clickText("继续阅读")
        compose.waitUntil(10_000) { app.store.data.value.history.first { it.comic.key == comic.key }.page == 1 }
        compose.runOnIdle { app.store.set("reader.whole", true); app.store.set("reader.mode", "continuous") }
        settle()
        assertEquals(1, app.store.data.value.history.first { it.comic.key == comic.key }.page)
        compose.runOnIdle { app.store.set("reader.comments", true); app.store.set("reader.mode", "curl") }
        settle()
        assertEquals(1, app.store.data.value.history.first { it.comic.key == comic.key }.page)
        compose.onRoot().performTouchInput { swipeLeft() }
        settle()
        compose.onRoot().performTouchInput { swipeLeft() }
        settle()
        capture("reader-before-comments-assert")
        compose.onNodeWithText("章节评论 · 第一章").assertIsDisplayed()
        capture("reader-chapter-comments")
        compose.runOnIdle { app.store.set("reader.mode", "ltr") }
        settle()
        capture("reader-chapter-comments-ltr")
        compose.onRoot().performTouchInput { swipeLeft() }
        settle()
        compose.waitUntil(10_000) { app.store.data.value.history.first { it.comic.key == comic.key }.chapter.id == "second" }
        clickDescription("返回")
        clickDescription("返回")
        clickDescription("搜索")
        clickDescription("搜索工具")
        capture("search-menu")
        clickText("常用搜索")
        compose.onNodeWithText("暂无常用搜索").assertExists()
        capture("saved-search-sheet")
        clickText("完成")
        archive.delete()
    }
    private fun settle() {
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        // Dialog windows attach on the Android frame clock before their Compose animation starts.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(350)
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
    }
    private fun clickText(text: String) { android.util.Log.i("PicaXTest", text); compose.onNodeWithText(text).performClick(); settle() }
    private fun clickDescription(text: String) { android.util.Log.i("PicaXTest", text); compose.onNodeWithContentDescription(text).performClick(); settle() }
    private fun capture(name: String) {
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) { "System screenshot was unavailable: $name" }
        val directory = File(compose.activity.filesDir, "verification").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
