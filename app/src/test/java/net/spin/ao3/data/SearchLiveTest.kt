package net.spin.ao3.data

import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * DIAGNOSTIC smoke test (live network): 5 distinct queries through the app's
 * real client to sample Cloudflare's intermittent tarpit. Print-only.
 */
class SearchLiveTest {

    @Test(timeout = 500_000)
    fun liveSearchNaruto() {
        val client = Ao3Client(cacheDir = null)
        val queries = listOf("naruto", "harry potter", "one piece", "sherlock", "avengers")
        queries.forEachIndexed { i, q ->
            val t0 = System.currentTimeMillis()
            try {
                val result = runBlocking {
                    client.search(SearchFilters(query = q), 1, SortOption.BEST_MATCH)
                }
                println("LIVE[$i]_$q OK: ${System.currentTimeMillis() - t0}ms works=${result.works.size}")
            } catch (e: Throwable) {
                println("LIVE[$i]_$q FAIL: ${System.currentTimeMillis() - t0}ms ${e::class.simpleName}: ${e.message}")
            }
        }
    }
}
