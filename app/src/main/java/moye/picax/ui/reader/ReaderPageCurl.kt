package moye.picax.ui.reader

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

/** Cylindrical page edge: both sampling and light vary across the curved paper. */
@Composable
fun Modifier.readerPageCurl(pager: androidx.compose.foundation.pager.PagerState, index: Int): Modifier {
    val shader = remember { RuntimeShader("""
        uniform shader page;
        uniform float2 resolution;
        uniform float progress;
        half4 main(float2 position) {
            float radius = max(1.0, resolution.x * 0.16 * sin(progress * 3.14159265));
            float axis = resolution.x * (1.0 - progress);
            float distance = position.x - axis;
            if (distance <= 0.0) return page.eval(position);
            if (distance > radius) return half4(0.0);
            float angle = asin(clamp(distance / radius, 0.0, 1.0));
            float sourceX = axis + radius * angle;
            if (sourceX >= resolution.x) return half4(0.0);
            half4 color = page.eval(float2(sourceX, position.y));
            float light = 0.62 + 0.38 * cos(angle);
            return half4(color.rgb * light, color.a);
        }
    """.trimIndent()) }
    return this.zIndex(if(pager.currentPage >= index) 1f else 0f).graphicsLayer {
        val offset = (pager.currentPage - index) + pager.currentPageOffsetFraction
        translationX = size.width * offset
        if(offset > 0f && offset < 1f) {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("progress", offset)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "page").asComposeRenderEffect()
        } else { renderEffect = null; alpha = if(offset >= 1f) 0f else 1f }
    }
}
