package moye.picax

import moye.picax.data.source.SourceSettings
import org.junit.Assert.*
import org.junit.Test

class SourceSettingsTest {
    @Test fun normalizesFrontendAndImageHostInputs() {
        assertEquals("https://example.com", SourceSettings.baseURL(" example.com/// ", "fallback"))
        assertEquals("https://default.example", SourceSettings.baseURL("  ", "https://default.example"))
        assertEquals("images.example.com", SourceSettings.domain("https://images.example.com/path/"))
    }
    @Test fun favoriteSortUsesSourceApiValues() {
        assertEquals("mr", SourceSettings.jmFavoriteSort("latest"))
        assertEquals("mp", SourceSettings.jmFavoriteSort("updated"))
        assertEquals("mp", SourceSettings.jmFavoriteSort("mp"))
    }
    @Test fun previousSettingsKeepTheirEffectiveValues() {
        val migrated = SourceSettings.migrate(mapOf("source.eHentai.url" to "https://exhentai.org", "jm.images" to "https://images.example.com", "jm.favoriteSort" to "mp", "updates.repository" to "youshen2/PicaX"))
        assertEquals("https://exhentai.org", migrated["frontend.eHentai"])
        assertEquals("4", migrated["jm.imageIndex"])
        assertEquals("updated", migrated["jm.favoriteSort"])
        assertEquals("youshen2/PicaX-Android", migrated["updates.repository"])
    }
}
