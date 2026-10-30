package moye.picax

import moye.picax.model.*
import org.junit.Assert.*
import org.junit.Test

class LibraryMatchingTest {
    @Test fun aliasesMatchWithoutMixingKnownLanguages() {
        val a = Comic("1", Platform.nhentai, "[Circle] 明日物語 | Tomorrow Story [Chinese]", language = "chinese")
        val b = Comic("2", Platform.hitomi, "明日物語", language = "中文")
        assertTrue(TitleMatcher.matches(a, b))
        assertFalse(TitleMatcher.matches(a, b.copy(language = "japanese")))
        assertFalse(TitleMatcher.matches(a.copy(title = "123"), b.copy(title = "123")))
    }
    @Test fun groupingMergesBridgedAliasesAndHonorsSeparation() {
        val a = Comic("a", Platform.picacg, "First Story")
        val b = Comic("b", Platform.jmComic, "Second Story")
        val bridge = Comic("c", Platform.hitomi, "First Story | Second Story")
        assertEquals(1, versionGroups(listOf(a, b, bridge), emptyList()).size)
        assertEquals(2, versionGroups(listOf(a, bridge), listOf(VersionOverride(bridge.key, separates = true))).size)
    }
    @Test fun readFilterConsidersTheWholeBookAndReportsReason() {
        val comic = Comic("1", Platform.picacg, "Sample")
        val chapter = Chapter("1", "First", 1)
        val data = AppData(preferences = mapOf("list.hideRead" to "true"), history = listOf(History(comic, chapter, 9, 10, chapterCount = 2)))
        assertEquals(1, evaluateComics(listOf(comic), data).visible.size)
        val complete = data.copy(history = listOf(History(comic, chapter.copy(order = 2), 9, 10, chapterCount = 2)))
        assertTrue(evaluateComics(listOf(comic), complete).hidden.first().reason.contains("100%"))
    }
}
