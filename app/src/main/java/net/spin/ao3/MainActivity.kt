package net.spin.ao3

import androidx.annotation.StringRes
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.ui.Route
import net.spin.ao3.ui.components.BottomBarDestination
import net.spin.ao3.ui.components.CapsuleBottomBar
import net.spin.ao3.ui.components.LocalBottomBarClearance
import net.spin.ao3.ui.components.PrimaryNavigationRail
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
        // La barra de estado sigue al TEMA DE LA APP, no al del sistema: con
        // «Claro» forzado sobre un sistema en oscuro, enableEdgeToEdge() por
        // defecto dejaba iconos claros sobre fondo blanco (invisibles).
        // reloadTheme() recrea la actividad al cambiar de tema, así que esto
        // se reevalúa siempre.
        val app = application as Ao3App
        val sysDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val dark = when (AppThemeMode.from(app.container.store.prefs.appThemeMode)) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> sysDark
        }
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        setContent { AppRoot() }
    }
}

/** The three top-level destinations of the navigation bar / rail. */
enum class AppTab(
    @get:StringRes override val labelRes: Int,
    override val icon: ImageVector,
) : BottomBarDestination {
    HOME(R.string.tab_home, Icons.Filled.Home),
    LIBRARY(R.string.tab_library, Icons.AutoMirrored.Filled.LibraryBooks),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
private fun AppRoot() {
    val app = LocalContext.current.applicationContext as Ao3App
    val container = app.container
    val store = container.store

    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    // Las subpáginas de Ajustes navegan con estado interno (no empujan rutas):
    // sin esto la píldora seguiría flotando sobre Apariencia/Lector y taparía
    // controles. Las pantallas lo reportan; se resetea al salir.
    var settingsSubOpen by rememberSaveable { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(AppThemeMode.from(store.prefs.appThemeMode)) }
    var dynamicColor by remember { mutableStateOf(store.prefs.dynamicColor) }
    // Recrear la actividad es el único remedio fiable al cambiar de tema.
    val activity = LocalContext.current as? Activity
    fun reloadTheme() {
        activity?.recreate()
    }

    val nav = rememberNavController()
    // SaveableStateHolder mantiene scroll, paginación y formularios de cada
    // pantalla a través de AnimatedContent y de la navegación.
    val screenStates = rememberSaveableStateHolder()
    val showTabs = nav.stack.size == 1

    fun browseTag(tag: String) = nav.push(Route.Search(SearchFilters(tag = tag), SortOption.KUDOS))

    BackHandler(enabled = nav.canGoBack || tab != AppTab.HOME) {
        if (nav.canGoBack) nav.pop() else tab = AppTab.HOME
    }

    // `key` fuerza a recrear TODO el árbol temático cuando cambia el tema.
    key(themeMode, dynamicColor) {
    Ao3Theme(mode = themeMode, dynamicColor = dynamicColor) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useRail = showTabs && maxWidth >= 600.dp && maxHeight >= 480.dp
            val railExpanded = maxWidth >= 1200.dp
            val motion = MaterialTheme.motionScheme
            Row(Modifier.fillMaxSize()) {
                if (useRail) {
                    PrimaryNavigationRail(
                        items = AppTab.entries,
                        selected = tab,
                        onSelect = { tab = it },
                        railExpanded = railExpanded,
                    )
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    bottomBar = {
                        // La píldora solo en el nivel superior; fuera cuando hay
                        // una subpágina de Ajustes abierta (navegación interna).
                        if (showTabs && !settingsSubOpen && !useRail) {
                            CapsuleBottomBar(
                                items = AppTab.entries,
                                selected = tab,
                                onSelect = { tab = it },
                            )
                        }
                    },
                ) { padding ->
                    // Sin padding inferior: el contenido fluye por detrás de la
                    // píldora flotante (look edge-to-edge). Las listas dejan su
                    // propio despeje con LocalBottomBarClearance para que el
                    // último ítem sea alcanzable.
                    Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                        CompositionLocalProvider(
                            LocalBottomBarClearance provides padding.calculateBottomPadding(),
                        ) {
                            val navTarget: NavTarget = if (showTabs) NavTarget.Tab(tab)
                                else NavTarget.Screen(nav.current, nav.stack.lastIndex)
                            AnimatedContent(
                            targetState = navTarget,
                            transitionSpec = {
                                if (initialState is NavTarget.Tab || targetState is NavTarget.Tab) {
                                    // Cambio de pestaña: la salida se corta seca (snap)
                                    // y la entrada aparece con fade + escala desde 92 %.
                                    // Así nunca coinciden dos pantallas a alfa intermedio
                                    // (el fallo del crossfade anterior: títulos grises).
                                    (fadeIn(motion.defaultEffectsSpec()) +
                                        scaleIn(
                                            animationSpec = motion.defaultSpatialSpec(),
                                            initialScale = 0.92f,
                                        )) togetherWith
                                        fadeOut(snap())
                                } else {
                                    val forward = nav.lastDirection >= 0
                                    if (forward) {
                                        (fadeIn(motion.defaultEffectsSpec()) +
                                            slideInHorizontally(motion.defaultSpatialSpec()) { it / 3 }) togetherWith
                                            (fadeOut(motion.defaultEffectsSpec()) +
                                                slideOutHorizontally(motion.defaultSpatialSpec()) { -it / 4 })
                                    } else {
                                        (fadeIn(motion.defaultEffectsSpec()) +
                                            slideInHorizontally(motion.defaultSpatialSpec()) { -it / 3 }) togetherWith
                                            (fadeOut(motion.defaultEffectsSpec()) +
                                                slideOutHorizontally(motion.defaultSpatialSpec()) { it / 4 })
                                    }
                                }
                            },
                            label = "nav",
                        ) { target ->
                            val slotKey = when (target) {
                                is NavTarget.Tab -> "tab-${target.tab.name}"
                                is NavTarget.Screen -> "screen@${target.slot}-${target.route.serialize()}"
                            }
                            screenStates.SaveableStateProvider(slotKey) {
                                when (target) {
                                is NavTarget.Tab -> when (target.tab) {
                                    AppTab.HOME -> HomeScreen(
                                        container = container,
                                        onSearch = { q, s -> nav.push(Route.Search(SearchFilters(query = q), s)) },
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
                                        onThemeModeChanged = { mode ->
                                            themeMode = mode
                                            reloadTheme()
                                        },
                                        onDynamicColorChanged = { dc ->
                                            dynamicColor = dc
                                            reloadTheme()
                                        },
                                        onSubpageChange = { settingsSubOpen = it },
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
                        } // CompositionLocalProvider
                    }
                }
            }
        }
    }
    } // key(themeMode, dynamicColor)
}

private sealed interface NavTarget {
    data class Tab(val tab: AppTab) : NavTarget
    data class Screen(val route: Route, val slot: Int) : NavTarget
}
