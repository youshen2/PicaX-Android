package moye.picax

import moye.picax.data.SwiftPreferenceMapping
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SwiftPreferenceMappingTest {
    @Test fun convertsReaderEnumsSecondsAndScaleWithoutLosingFalse() {
        val values = mapOf(
            "settings.reader.readingMode" to JSONObject("{\"stringValue\":\"pageCurl\"}"),
            "settings.reader.imageRetryInterval" to JSONObject("{\"doubleValue\":1.2}"),
            "settings.reader.doubleTapZoomScale" to JSONObject("{\"doubleValue\":1.75}"),
            "settings.reader.pinchZoomEnabled" to JSONObject("{\"boolValue\":false}")
        )
        val mapped = SwiftPreferenceMapping.read(values)
        assertEquals("curl", mapped["reader.mode"])
        assertEquals("1200", mapped["reader.retryDelay"])
        assertEquals("175", mapped["reader.doubleScale"])
        assertEquals("false", mapped["reader.zoomPinch"])
    }
}
