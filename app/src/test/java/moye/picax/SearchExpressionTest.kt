package moye.picax

import moye.picax.model.SearchExpression
import moye.picax.data.DocumentTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchExpressionTest {
    @Test fun combinationsRespectPrecedence() {
        assertEquals(listOf("A C", "B C"), SearchExpression.expand("(A/B)&C"))
        assertEquals(listOf("A", "B C"), SearchExpression.expand("A/B&C"))
    }
    @Test fun quotedTagsKeepOperatorsLiteral() {
        assertEquals(listOf("\"tag:a/b\" \"C&D\""), SearchExpression.expand("\"tag:a/b\" & \"C&D\""))
        assertEquals(listOf("A C", "B C"), SearchExpression.expand("（A/B）& C"))
    }
    @Test fun malformedQueriesFailBeforeNetworking() {
        listOf("(A/B", "A(B/C)", "A)", "(A/B)(C/D)").forEach { query ->
            assertTrue(query, runCatching { SearchExpression.expand(query) }.isFailure)
        }
    }
    @Test fun sourceSpecificKeywordsPreservePhraseBoundaries() {
        val clause = SearchExpression.clauses("full color & sample tag").single()
        assertEquals("\"full color\" \"sample tag\"", clause.keyword(moye.picax.model.Platform.nhentai))
        assertEquals("full color sample tag", clause.keyword(moye.picax.model.Platform.picacg))
        assertEquals(listOf("A"), SearchExpression.expand(" / A && / "))
    }
    @Test fun pagesUseNaturalFilenameOrder() {
        val files = listOf("page10.png", "page2.png", "page1.png")
        assertEquals(listOf("page1.png", "page2.png", "page10.png"), files.sortedWith(DocumentTools::naturalCompare))
    }
}
