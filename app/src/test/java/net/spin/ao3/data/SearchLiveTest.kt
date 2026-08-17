package net.spin.ao3.data

import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * DIAGNOSTIC smoke test (live network): runs the app's real search path against
 * archiveofourown.org and prints the outcome (timing, works, or the exact
 * failure). Print-only so a throttled/blocked CI IP never turns the pipeline
 * red — the printed line is the signal. TTL is generous because AO3 can be
 * slow under Cloudflare.
 */
class SearchLiveTest {

    @Test(timeout = 150_000)
    fun liveSearchNaruto() {
        val client = Ao3Client(cacheDir = null)
        val t0 = System.currentTimeMillis()
        try {
            val result = runBlocking {
                client.search(SearchFilters(query = "naruto"), 1, SortOption.BEST_MATCH)
            }
            val ms = System.currentTimeMillis() - t0
            println("LIVE_SEARCH_OK: ${ms}ms works=${result.works.size} total=${result.total} filtersApplied=${result.filtersApplied}")
        } catch (e: Throwable) {
            val ms = System.currentTimeMillis() - t0
            println("LIVE_SEARCH_FAIL: ${ms}ms ${e::class.simpleName}: ${e.message}")
        }
    }
}
