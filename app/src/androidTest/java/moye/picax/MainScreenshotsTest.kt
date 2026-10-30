package moye.picax

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import moye.picax.model.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MainScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeLibraryAndSearchScreenshots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PicaXApplication
        val original = app.store.data.value
        val titles = listOf("山间手记", "海岸来信", "秋日散步", "漫长旅途")
        val colors = listOf(0xff45634f.toInt(), 0xff5b8191.toInt(), 0xffa77249.toInt(), 0xff726686.toInt())
        val comics = titles.mapIndexed { index, title ->
            val bitmap = Bitmap.createBitmap(360, 500, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(colors[index])
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfff6e7c7.toInt() }
            canvas.drawCircle(255f, 115f, 55f, paint)
            paint.color = 0x779fc2b4
            canvas.drawRect(0f, 275f, 360f, 500f, paint)
            paint.color = 0xfff9f3e8.toInt(); paint.textSize = 42f
            canvas.drawText(title, 26f, 390f, paint)
            paint.textSize = 16f; canvas.drawText("PICAX · 本地演示", 26f, 452f, paint)
            val file = File(app.filesDir, "readme-cover-$index.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            Comic("demo-$index", Platform.local, title, "本地演示文档", Uri.fromFile(file).toString(), listOf("旅行", "日常"), pages = 24)
        }
        app.store.update { AppData(
            preferences = mapOf("adult" to "true", "onboarding" to "true", "appearance" to "light", "search.focus" to "false"),
            history = comics.take(3).mapIndexed { i, comic -> History(comic, Chapter("chapter", "第一话"), 3 + i * 4, 24) },
            later = comics.reversed(), favorites = comics.map { Favorite(it) },
            searches = listOf("旅行", "日常", "冒险").map { SearchRecord(it, listOf(Platform.picacg, Platform.jmComic)) }
        ) }
        try {
            fun capture(name: String) {
                compose.mainClock.advanceTimeBy(1500); compose.waitForIdle()
                val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val directory = File(app.filesDir, "readme-screenshots").apply { mkdirs() }
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            compose.onNodeWithText("历史记录").assertExists()
            capture("home")
            compose.onNodeWithContentDescription("收藏").performClick()
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithText("默认收藏夹").performClick()
            capture("library")
            compose.onNodeWithContentDescription("返回").performClick()
            compose.mainClock.advanceTimeBy(1200)
            compose.onNodeWithContentDescription("搜索").performClick()
            capture("search")
        } finally { app.store.update { original } }
    }
}
