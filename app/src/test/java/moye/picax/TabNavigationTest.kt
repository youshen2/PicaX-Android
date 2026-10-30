package moye.picax

import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationOperation
import moye.picax.ui.*
import org.junit.Assert.*
import org.junit.Test

class TabNavigationTest {
    @Test fun tabDirectionFollowsBarOrderEvenWhenRevisitingTabs() {
        val sequence = listOf("home", "favorites", "search", "home", "categories", "favorites", "explore")
        val expected = listOf(1, 1, -1, 1, -1, 1)
        sequence.zipWithNext().zip(expected).forEach { (pair, direction) ->
            val from = Route(pair.first); val to = Route(pair.second)
            assertEquals("${pair.first} → ${pair.second}", direction, mainTabDirection(from, to))
            assertEquals(GlasmioNavigationOperation.Tab, navigationOperation(from, to, isPop = true))
            assertEquals(GlasmioNavigationOperation.Tab, navigationOperation(from, to, isPop = false))
        }
    }

    @Test fun detailAndScopedSearchKeepStackNavigation() {
        val categories = Route("categories")
        val search = Route("search", parameter = "language:chinese")
        assertFalse(search.isMainTab)
        assertNull(mainTabDirection(categories, search))
        assertEquals(GlasmioNavigationOperation.Push, navigationOperation(categories, search, false))
        assertEquals(GlasmioNavigationOperation.Pop, navigationOperation(search, categories, true))
        assertNull(mainTabDirection(Route("detail"), Route("home")))
        assertNull(mainTabDirection(Route("home"), Route("home")))
    }
}
