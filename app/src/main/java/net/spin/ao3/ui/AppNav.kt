package net.spin.ao3.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption

/** App navigation routes. */
sealed class Route {
    data object Home : Route()
    data class Search(val filters: SearchFilters, val sort: SortOption) : Route()
    data class Detail(val workId: Long) : Route()
    data class Reader(val workId: Long, val chapterIndex: Int) : Route()
    data class Author(val username: String) : Route()

    fun serialize(): String = when (this) {
        Home -> "home"
        is Search -> "search\u0001${filters.serialize()}\u0001${sort.name}"
        is Detail -> "detail\u0001$workId"
        is Reader -> "reader\u0001$workId\u0001$chapterIndex"
        is Author -> "author\u0001$username"
    }

    companion object {
        fun deserialize(s: String): Route {
            val p = s.split("\u0001")
            return when (p.getOrNull(0)) {
                "search" -> Route.Search(
                    SearchFilters.parse(p.getOrNull(1) ?: ""),
                    runCatching { SortOption.valueOf(p.getOrNull(2) ?: "BEST_MATCH") }.getOrDefault(SortOption.BEST_MATCH),
                )
                "detail" -> Route.Detail(p.getOrNull(1)?.toLongOrNull() ?: 0L)
                "reader" -> Route.Reader(
                    p.getOrNull(1)?.toLongOrNull() ?: 0L,
                    p.getOrNull(2)?.toIntOrNull() ?: 0,
                )
                "author" -> Route.Author(p.getOrNull(1) ?: "")
                else -> Route.Home
            }
        }
    }
}

/** Simple back-stack navigation, saveable across configuration changes. */
class NavController(initial: List<Route> = listOf(Route.Home)) {
    private val _stack = mutableStateListOf<Route>().apply { addAll(initial) }
    val stack: List<Route> get() = _stack
    val current: Route get() = _stack.last()
    val canGoBack: Boolean get() = _stack.size > 1

    /** +1 for a push, -1 for a pop; drives the enter/exit slide direction. */
    var lastDirection: Int = 1
        private set

    fun push(route: Route) {
        if (_stack.lastOrNull() != route) {
            lastDirection = 1
            _stack.add(route)
        }
    }

    fun pop() {
        if (_stack.size > 1) {
            lastDirection = -1
            _stack.removeAt(_stack.lastIndex)
        }
    }
}

@Composable
fun rememberNavController(): NavController {
    val saver = listSaver<NavController, String>(
        save = { it.stack.map(Route::serialize) },
        restore = { NavController(it.map(Route.Companion::deserialize)) },
    )
    return rememberSaveable(saver = saver) { NavController() }
}
