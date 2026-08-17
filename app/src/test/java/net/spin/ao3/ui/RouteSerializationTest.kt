package net.spin.ao3.ui

import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for [Route.serialize]/[Route.deserialize]. The old encoding
 * used raw \u0001 separators, so a query or username containing that character
 * broke the round-trip (lost navigation state on rotation). Fields are now
 * URL-encoded.
 */
class RouteSerializationTest {

    @Test
    fun `search route round trips with hostile query`() {
        val route = Route.Search(
            SearchFilters(query = "query with \u0001 and \u0002 and \"quotes\" and áccénts"),
            SortOption.KUDOS,
        )
        assertEquals(route, Route.deserialize(route.serialize()))
    }

    @Test
    fun `search route preserves sort and full filters`() {
        val route = Route.Search(
            SearchFilters(query = "harry potter", tag = "Harry Potter - J. K. Rowling", rating = 13, warnings = setOf(14, 17)),
            SortOption.WORDS,
        )
        assertEquals(route, Route.deserialize(route.serialize()))
    }

    @Test
    fun `detail and reader routes round trip`() {
        val detail = Route.Detail(123456789L)
        assertEquals(detail, Route.deserialize(detail.serialize()))

        val reader = Route.Reader(123456789L, 4)
        assertEquals(reader, Route.deserialize(reader.serialize()))
    }

    @Test
    fun `author route round trips with username characters`() {
        val a1 = Route.Author("Some_Name-With.Dots")
        assertEquals(a1, Route.deserialize(a1.serialize()))

        val a2 = Route.Author("user with spaces & symbols")
        assertEquals(a2, Route.deserialize(a2.serialize()))
    }

    @Test
    fun `home deserializes by default on garbage`() {
        assertEquals(Route.Home, Route.deserialize("nonsense"))
        assertEquals(Route.Home, Route.deserialize(""))
    }
}
