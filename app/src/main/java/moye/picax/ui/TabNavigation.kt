package moye.picax.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.scene.Scene
import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationOperation
import kotlinx.serialization.json.Json

internal val mainTabScreens = listOf("home", "favorites", "explore", "categories", "search")
internal val Route.isMainTab: Boolean
    get() = screen in mainTabScreens && parameter.isEmpty()

/** Positive means the destination sits to the right in the bottom navigation bar. */
internal fun mainTabDirection(from: Route, to: Route): Int? {
    if(!from.isMainTab || !to.isMainTab || from.screen == to.screen) return null
    return mainTabScreens.indexOf(to.screen).compareTo(mainTabScreens.indexOf(from.screen))
}

internal fun navigationOperation(from: Route, to: Route, isPop: Boolean): GlasmioNavigationOperation =
    if(mainTabDirection(from, to) != null) GlasmioNavigationOperation.Tab
    else if(isPop) GlasmioNavigationOperation.Pop else GlasmioNavigationOperation.Push

/** Resolve both push and pop callbacks from their actual endpoints, including revisited tabs. */
internal fun tabAwareTransition(
    json: Json,
    layoutDirection: LayoutDirection,
    fallback: AnimatedContentTransitionScope<Scene<String>>.() -> ContentTransform,
): AnimatedContentTransitionScope<Scene<String>>.() -> ContentTransform = {
    val from = json.decodeFromString<Route>(initialState.entries.last().contentKey as String)
    val to = json.decodeFromString<Route>(targetState.entries.last().contentKey as String)
    val direction = mainTabDirection(from, to)
    if(direction == null) fallback()
    else {
        val sign = direction * if(layoutDirection == LayoutDirection.Ltr) 1 else -1
        ContentTransform(
            slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> width * sign },
            slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> -width * sign },
        )
    }
}
