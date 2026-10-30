package moye.picax.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.navigation3.runtime.NavEntry
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.icons.*
import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationBarWithTrailingItem
import io.github.neoruaa.glasmio.scaffold.navigation.*
import io.github.neoruaa.glasmio.ui.GlasmioTabItem
import kotlinx.serialization.encodeToString
import moye.picax.data.*
import moye.picax.ui.components.ErrorDialog
import moye.picax.ui.components.PicaXIcons
import moye.picax.ui.home.*
import moye.picax.ui.library.*
import moye.picax.ui.reader.ReaderPage
import moye.picax.ui.search.*
import moye.picax.ui.settings.*

@Composable fun PicaXApp(store: AppStore, repository: ComicRepository, incomingLink: String?, consumeLink: () -> Unit, clipboardLink: String? = null, consumeClipboard: () -> Unit = {}) {
    val preferences by store.preferences.collectAsState()
    val appearance = preferences["appearance"] ?: "system"
    val dark = if(appearance == "system") isSystemInDarkTheme() else appearance == "dark"
    val activity = LocalActivity.current
    val stack = rememberSaveable(saver = listSaver(
        save = { it.toList() }, restore = { values: List<String> -> mutableStateListOf(*values.toTypedArray()) }
    )) { mutableStateListOf(store.json.encodeToString(Route("home"))) }
    val navigator = remember(stack) { Navigator(store, stack) }
    SideEffect { activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars = !dark && navigator.current.screen != "reader" } }
    val app = remember { AppEnvironment(store, repository, navigator) }
    var startupRelease by remember { mutableStateOf<AppRelease?>(null) }
    LaunchedEffect(Unit) {
        if(store.bool("updates.onLaunch")) runCatching { AppUpdates.check(repository) }.getOrNull()?.let { release ->
            if(release.apk != null && release.version.trimStart('v') != moye.picax.BuildConfig.VERSION_NAME) startupRelease = release
        }
    }
    var linkError by remember { mutableStateOf<String?>(null) }
    val ready = store.bool("adult") && store.bool("onboarding")
    val maintenance = listOf("follow.enabled", "follow.minutes", "picacg.autoCheckIn", "jm.autoCheckIn", "webdav.auto").map { preferences[it] }
    LaunchedEffect(maintenance) { MaintenanceWorker.schedule(store) }
    LaunchedEffect(incomingLink, ready) {
        if(incomingLink != null && ready) {
            val uri = android.net.Uri.parse(incomingLink)
            val target = if(uri.scheme == "picax") uri.getQueryParameter("url") ?: incomingLink else incomingLink
            runCatching { repository.fromLink(target) }.onSuccess(navigator::detail).onFailure { linkError = it.message }
            consumeLink()
        }
    }
    CompositionLocalProvider(LocalApp provides app) {
        GlasmioTheme(isDark = dark) {
            GlasmioIconProvider(resolver = PicaXIcons) {
            GlasmioBackdropHost(Modifier.fillMaxSize(), sampledContent = {
                Box(Modifier.fillMaxSize().background(GlasmioTheme.colors.groupedBackground))
            }, overlayContent = {
                when {
                    !store.bool("adult") -> AgePage()
                    !store.bool("onboarding") -> OnboardingPage()
                    else -> MainNavigation()
                }
                if(ready && clipboardLink != null) io.github.neoruaa.glasmio.ui.GlasmioAlertDialog(consumeClipboard, "发现漫画链接", clipboardLink, buttons = listOf(
                    io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec("取消", consumeClipboard),
                    io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec("打开", { navigator.detail(repository.fromLink(clipboardLink)); consumeClipboard() })))
                startupRelease?.let { release -> io.github.neoruaa.glasmio.ui.GlasmioAlertDialog({ startupRelease = null }, "发现新版本 ${release.version}", release.notes.take(500), buttons = listOf(
                    io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec("稍后", { startupRelease = null }),
                    io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec("查看更新", { startupRelease = null; navigator.open(Route("appUpdates")) }))) }
                ErrorDialog(linkError) { linkError = null }
            })
            }
        }
    }
}

@Composable private fun MainNavigation() {
    val app = LocalApp.current
    val nav = app.navigator
    val current = nav.current
    val pageStates = rememberSaveableStateHolder()
    val motion = remember { GlasmioNavHeaderMotion(nav.stack.last()) { from, to, pop ->
        val a = app.store.json.decodeFromString<Route>(from as String)
        val b = app.store.json.decodeFromString<Route>(to as String)
        navigationOperation(a, b, pop)
    } }
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current.density
    val push = remember(layoutDirection, density) {
        tabAwareTransition(app.store.json, layoutDirection, iosTransitionSpec<String>(layoutDirection, density))
    }
    val pop = remember(layoutDirection, density) {
        tabAwareTransition(app.store.json, layoutDirection, iosPopTransitionSpec<String>(layoutDirection, density))
    }
    GlasmioNavDisplay(backStack = nav.stack, motion = motion, onBack = nav::back,
        transitionSpec = push, popTransitionSpec = pop, entryProvider = { key ->
        NavEntry(key = key, contentKey = key) {
            val route = remember(key) { app.store.json.decodeFromString<Route>(key) }
            pageStates.SaveableStateProvider(route.instance) {
            when(route.screen) {
                "home" -> HomePage()
                "favorites" -> FavoritesPage()
                "explore" -> ExplorePage()
                "categories" -> CategoriesPage()
                "search" -> SearchPage(route)
                "results" -> ResultsPage(route)
                "detail" -> DetailPage(requireNotNull(route.comic), route.offline)
                "reader" -> ReaderPage(route)
                "downloads" -> DownloadsPage()
                "library" -> LibraryPage(route)
                "statistics" -> StatisticsPage()
                "settings" -> SettingsPage()
                "accounts" -> AccountsPage()
                "account" -> AccountPage(route.platform)
                "web" -> WebLoginPage(route.platform)
                "preferences" -> PreferencesPage(route.parameter)
                "blocking" -> BlockingPage()
                "storage" -> StoragePage()
                "backup" -> BackupPage()
                "about" -> AboutPage()
                "tagDatabase" -> TagDatabasePage()
                "appUpdates" -> UpdateSettingsPage()
                "legal" -> LegalPage(route.parameter)
                "updates" -> FollowUpdatesPage()
                "versions" -> VersionsPage(route)
                "folders" -> CloudFoldersPage(route.platform)
                "source" -> SourceFeaturesPage(route.platform, showsAccount = route.parameter != "account")
                "ehProfiles" -> EhProfilesPage()
                "ownComments" -> OwnCommentsPage(route.platform)
                "statisticsDetail" -> StatisticsPage(route.comic)
            }
            }
        }
    })
    if(current.isMainTab) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            GlasmioNavigationBarWithTrailingItem(
                items = listOf(GlasmioTabItem("home", "主页", SfSymbol.House), GlasmioTabItem("favorites", "收藏", SfSymbol.Heart),
                    GlasmioTabItem("explore", "发现", SfSymbol.Safari), GlasmioTabItem("categories", "分类", SfSymbol.Layers)),
                trailingItem = GlasmioTabItem("search", "搜索", SfSymbol.Search), selectedKey = current.screen,
                onSelected = nav::tab, onExpand = {}, compactProgress = remember { mutableFloatStateOf(0f) },
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
    AppLockOverlay()
}
