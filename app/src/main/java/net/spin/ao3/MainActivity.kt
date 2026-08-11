package net.spin.ao3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.ui.Route
import net.spin.ao3.ui.components.BottomBarDestination
import net.spin.ao3.ui.components.CapsuleBottomBar
import net.spin.ao3.ui.rememberNavController
import net.spin.ao3.ui.screens.AuthorScreen
import net.spin.ao3.ui.screens.HomeScreen
import net.spin.ao3.ui.screens.LibraryScreen
import net.spin.ao3.ui.screens.ReaderScreen
import net.spin.ao3.ui.screens.SearchScreen
import net.spin.ao3.ui.screens.SettingsScreen
import net.spin.ao3.ui.screens.WorkDetailScreen
import net.spin.ao3.ui.theme.Ao3Theme
import net.spin.ao3.ui.theme.AppThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

/** The three top-level destinations of the bottom navigation bar. */
enum class AppTab(override val label: String, override val icon: ImageVector) : BottomBarDestination {
    HOME("Inicio", Icons.Filled.Home),
    LIBRARY("Biblioteca", Icons.Filled.List),
    SETTINGS("Ajustes", Icons.Filled.Settings),
}

@Composable
private fun AppRoot() {
    val app = LocalContext.current.applicationContext as Ao3App
    val container = app.container
    val store = container.store

    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var themeMode by remember { mutableStateOf(AppThemeMode.from(store.prefs.appThemeMode)) }
    var dynamicColor by remember { mutableStateOf(store.prefs.dynamicColor) }

    // Stack for Search / Detail / Reader (full-screen, hidden bottom bar).
    val nav = rememberNavController()
    val showTabs = nav.stack.size == 1

    fun browseTag(tag: String) = nav.push(Route.Search(SearchFilters(tag = tag), SortOption.KUDOS))

    BackHandler(enabled = nav.canGoBack || tab != AppTab.HOME) {
        if (nav.canGoBack) nav.pop() else tab = AppTab.HOME
    }

    Ao3Theme(mode = themeMode, dynamicColor = dynamicColor) {
        Scaffold(
            // Every screen draws its own status-bar insets (TopAppBar etc.);
            // consuming them here too would double the top padding.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTabs) {
                    CapsuleBottomBar(
                        items = AppTab.entries,
                        selected = tab,
                        onSelect = { tab = it },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = if (showTabs) NavTarget.Tab(tab) else NavTarget.Screen(nav.current),
                    transitionSpec = {
                        // Tab switches are a plain crossfade (no slide — they are
                        // not spatial navigation and felt wrong with a slide).
                        // Screen pushes slide in from the right; pops slide in
                        // from the left, matching the physical direction.
                        if (initialState is NavTarget.Tab || targetState is NavTarget.Tab) {
                            (fadeIn(tween(200, easing = StandardDecelerate)) togetherWith
                                fadeOut(tween(120)))
                        } else {
                            val forward = nav.lastDirection >= 0
                            if (forward) {
                                (fadeIn(tween(240, easing = StandardDecelerate)) +
                                    slideInHorizontally(tween(300, easing = StandardDecelerate)) { it / 3 }) togetherWith
                                    (fadeOut(tween(180, easing = StandardAccelerate)) +
                                        slideOutHorizontally(tween(300, easing = StandardAccelerate)) { -it / 4 })
                            } else {
                                (fadeIn(tween(240, easing = StandardDecelerate)) +
                                    slideInHorizontally(tween(300, easing = StandardDecelerate)) { -it / 3 }) togetherWith
                                    (fadeOut(tween(180, easing = StandardAccelerate)) +
                                        slideOutHorizontally(tween(300, easing = StandardAccelerate)) { it / 4 })
                            }
                        }
                    },
                    label = "nav",
                ) { target ->
                    when (target) {
                        is NavTarget.Tab -> when (target.tab) {
                            AppTab.HOME -> HomeScreen(
                                container = container,
                                onSearch = { q, s -> nav.push(Route.Search(SearchFilters(query = q), s)) },
                                onBrowseTag = { tag -> browseTag(tag) },
                                onOpenDetail = { nav.push(Route.Detail(it)) },
                                onOpenReader = { id, ch -> nav.push(Route.Reader(id, ch)) },
                            )
                            AppTab.LIBRARY -> LibraryScreen(
                                container = container,
                                onOpenDetail = { nav.push(Route.Detail(it)) },
                                onOpenReader = { id, ch -> nav.push(Route.Reader(id, ch)) },
                                onExplore = { tab = AppTab.HOME },
                            )
                            AppTab.SETTINGS -> SettingsScreen(
                                container = container,
                                onThemeModeChanged = { mode -> themeMode = mode },
                                onDynamicColorChanged = { dc -> dynamicColor = dc },
                            )
                        }
                        is NavTarget.Screen -> when (val route = target.route) {
                            is Route.Search -> SearchScreen(
                                container = container,
                                filters = route.filters,
                                sort = route.sort,
                                onBack = { nav.pop() },
                                onOpenDetail = { nav.push(Route.Detail(it)) },
                                onOpenTag = { tag -> browseTag(tag) },
                                onOpenAuthor = { name -> nav.push(Route.Author(name)) },
                            )
                            is Route.Detail -> WorkDetailScreen(
                                container = container,
                                workId = route.workId,
                                onBack = { nav.pop() },
                                onOpenChapter = { ch -> nav.push(Route.Reader(route.workId, ch)) },
                                onOpenTag = { tag -> browseTag(tag) },
                                onOpenAuthor = { name -> nav.push(Route.Author(name)) },
                            )
                            is Route.Author -> AuthorScreen(
                                container = container,
                                username = route.username,
                                onBack = { nav.pop() },
                                onOpenDetail = { nav.push(Route.Detail(it)) },
                                onOpenTag = { tag -> browseTag(tag) },
                            )
                            is Route.Reader -> ReaderScreen(
                                container = container,
                                workId = route.workId,
                                initialChapter = route.chapterIndex,
                                onBack = { nav.pop() },
                                onOpenWork = {
                                    nav.pop()
                                    nav.push(Route.Detail(route.workId))
                                },
                            )
                            Route.Home -> Unit
                        }
                    }
                }
            }
        }
    }
}

private sealed interface NavTarget {
    data class Tab(val tab: AppTab) : NavTarget
    data class Screen(val route: Route) : NavTarget
}

// MD3 standard motion easing: cubic-bezier(0,0,0,1) for enter (decelerate),
// cubic-bezier(0.3,0,1,1) for exit (accelerate).
private val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
private val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
