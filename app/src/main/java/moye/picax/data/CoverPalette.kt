package moye.picax.data

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.picax.model.Comic

class CoverPalette(private val images: PageImages) {
    private val colors = LruCache<String, Int>(100)
    suspend fun color(comic: Comic): Int? = withContext(Dispatchers.IO) {
        if(comic.cover.isBlank()) return@withContext null
        colors.get(comic.cover)?.let { return@withContext it }
        val file = images.file(comic.cover, comic.platform)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; BitmapFactory.decodeFile(file.path, this) }
        val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 64).coerceAtLeast(1) }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return@withContext null
        try {
            val histogram = mutableMapOf<Int, Int>()
            for(y in 0 until bitmap.height step 2) for(x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if(Color.alpha(pixel) < 128) continue
                val r = Color.red(pixel) / 24 * 24; val g = Color.green(pixel) / 24 * 24; val b = Color.blue(pixel) / 24 * 24
                if(maxOf(r, g, b) - minOf(r, g, b) < 30 || r + g + b < 75 || r + g + b > 690) continue
                val color = Color.rgb(r, g, b); histogram[color] = (histogram[color] ?: 0) + 1
            }
            histogram.maxByOrNull { it.value }?.key?.also { colors.put(comic.cover, it) }
        } finally { bitmap.recycle() }
    }
}
