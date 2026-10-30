package moye.picax

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import io.github.neoruaa.glasmio.scaffold.navigation.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moye.picax.ui.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TabMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bodySlidesTowardTheSelectedTabInBothDirections() {
        val json = Json { encodeDefaults = true }
        val keys = mainTabScreens.associateWith { json.encodeToString(Route(it)) }
        val stack = mutableStateListOf(keys.getValue("home"))
        val motion = GlasmioNavHeaderMotion(stack.last()) { from, to, pop ->
            navigationOperation(json.decodeFromString(from as String), json.decodeFromString(to as String), pop)
        }
        compose.setContent {
            GlasmioNavDisplay(stack, motion,
                transitionSpec = tabAwareTransition(json, LayoutDirection.Ltr, iosTransitionSpec()),
                popTransitionSpec = tabAwareTransition(json, LayoutDirection.Ltr, iosPopTransitionSpec()),
                entryProvider = { key -> NavEntry(key, contentKey = key) {
                    val screen = json.decodeFromString<Route>(key).screen
                    Box(Modifier.fillMaxSize().background(Color.White).testTag("page-$screen"))
                } })
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        fun moveTo(screen: String, fromRight: Boolean) {
            compose.runOnIdle { stack.clear(); stack.add(keys.getValue(screen)) }
            compose.mainClock.advanceTimeBy(80)
            val left = compose.onNodeWithTag("page-$screen").getUnclippedBoundsInRoot().left.value
            assertTrue("$screen should enter from ${if(fromRight) "right" else "left"}, left=$left", if(fromRight) left > 0f else left < 0f)
            compose.mainClock.advanceTimeBy(1_200)
            compose.onNodeWithTag("page-$screen").assertIsDisplayed()
        }
        moveTo("favorites", fromRight = true)
        moveTo("home", fromRight = false)
        moveTo("search", fromRight = true)
        moveTo("favorites", fromRight = false)
    }
}
