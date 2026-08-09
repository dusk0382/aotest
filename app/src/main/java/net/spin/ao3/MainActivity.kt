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
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.ui.Route
import net.spin.ao3.ui.rememberNavController
import net.spin.ao3.ui.screens.HomeScreen
import net.spin.ao3.ui.screens.ReaderScreen
import net.spin.ao3.ui.screens.SearchScreen
import net.spin.ao3.ui.screens.WorkDetailScreen
import net.spin.ao3.ui.theme.Ao3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Ao3Theme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Ao3App
    val container = app.container
    val nav = rememberNavController()

    BackHandler(enabled = nav.canGoBack) { nav.pop() }

    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 5 }) togetherWith
                (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 5 })
        },
        label = "nav",
    ) { route ->
        when (route) {
            Route.Home -> HomeScreen(
                container = container,
                onSearch = { q, s -> nav.push(Route.Search(SearchFilters(query = q), s)) },
                onBrowseTag = { tag -> nav.push(Route.Search(SearchFilters(tag = tag), SortOption.KUDOS)) },
                onOpenDetail = { nav.push(Route.Detail(it)) },
                onOpenReader = { id, ch -> nav.push(Route.Reader(id, ch)) },
            )
            is Route.Search -> SearchScreen(
                container = container,
                filters = route.filters,
                sort = route.sort,
                onBack = { nav.pop() },
                onOpenDetail = { nav.push(Route.Detail(it)) },
            )
            is Route.Detail -> WorkDetailScreen(
                container = container,
                workId = route.workId,
                onBack = { nav.pop() },
                onOpenChapter = { ch -> nav.push(Route.Reader(route.workId, ch)) },
            )
            is Route.Reader -> ReaderScreen(
                container = container,
                workId = route.workId,
                initialChapter = route.chapterIndex,
                onBack = { nav.pop() },
            )
        }
    }
}
