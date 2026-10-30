package moye.picax

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GlassRecordingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibleContentIsRecordedOncePerInvalidation() {
        val color = mutableStateOf(Color.Red)
        val draws = AtomicInteger()
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.size(80.dp).testTag("sample").layerBackdrop(backdrop).drawWithContent {
                draws.incrementAndGet()
                drawRect(color.value)
                drawContent()
            })
        }
        compose.waitForIdle()
        compose.runOnIdle { draws.set(0); color.value = Color.Blue }
        compose.waitForIdle()
        assertEquals("One source traversal for a single invalidation", 1, draws.get())
        val pixel = compose.onNodeWithTag("sample").captureToImage().toPixelMap()[20, 20]
        assertEquals(Color.Blue, pixel)
    }

    @Test fun customCaptureDoesNotReplaceVisibleContent() {
        compose.setContent {
            val backdrop = rememberLayerBackdrop(onDraw = { drawRect(Color.Red) })
            Box(Modifier.size(80.dp).testTag("custom").layerBackdrop(backdrop).drawWithContent { drawRect(Color.Blue) })
        }
        compose.waitForIdle()
        assertEquals(Color.Blue, compose.onNodeWithTag("custom").captureToImage().toPixelMap()[20, 20])
    }
}
