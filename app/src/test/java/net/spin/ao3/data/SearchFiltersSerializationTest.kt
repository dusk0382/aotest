package net.spin.ao3.data

import net.spin.ao3.data.model.SearchFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Regression tests for [SearchFilters.serialize]/[SearchFilters.parse]. The old
 * implementation joined raw fields with \u0002, so a query containing that
 * character (or the \u0001 used by Route) broke the round-trip. Fields are now
 * URL-encoded, so any input must survive.
 */
class SearchFiltersSerializationTest {

    @Test
    fun `round trip preserves a typical filter set`() {
        val filters = SearchFilters(
            query = "naruto sasuke",
            tag = "Naruto (Anime & Manga)",
            includeTags = "fluff, slow burn",
            excludeTags = "mpreg",
            rating = 12,
            warnings = setOf(14, 17),
            categories = setOf(23),
            excludeRating = 13,
            excludeWarnings = setOf(19),
            excludeCategories = setOf(21),
            completeOnly = true,
            excludeCrossover = true,
            language = "es",
            wordsFrom = "1000",
            wordsTo = "50000",
            dateFrom = "2020-01-01",
            dateTo = "2024-12-31",
            fandomIds = setOf(1L, 2L),
            characterIds = setOf(3L),
            relationshipIds = setOf(4L),
            freeformIds = setOf(5L),
            excludeFandomIds = setOf(6L),
            excludeCharacterIds = setOf(7L),
            excludeRelationshipIds = setOf(8L),
            excludeFreeformIds = setOf(9L),
        )
        assertEquals(filters, SearchFilters.parse(filters.serialize()))
    }

    @Test
    fun `round trip survives hostile characters`() {
        val q = "query with \u0001 \u0002 separators, \"quotes\", & ampersand, + plus, % percent, áccénts, emoji 😀"
        val filters = SearchFilters(
            query = q,
            tag = "Tag/With\\Slashes & Stuff",
            includeTags = "a,b,\u0002c",
        )
        assertEquals(filters, SearchFilters.parse(filters.serialize()))
    }

    @Test
    fun `empty filters round trip`() {
        assertEquals(SearchFilters(), SearchFilters.parse(SearchFilters().serialize()))
    }

    @Test
    fun `serialize is stable for cache keys`() {
        // The same filters must always serialize identically (used as a cache key).
        val a = SearchFilters(query = "naruto", rating = 12, warnings = setOf(14, 17))
        val b = SearchFilters(query = "naruto", rating = 12, warnings = setOf(17, 14))
        assertEquals(a.serialize(), b.serialize())
        // ...but differ from a different filter set.
        val c = SearchFilters(query = "naruto", rating = 13, warnings = setOf(14, 17))
        assertNotEquals(a.serialize(), c.serialize())
    }

    @Test
    fun `canonical tag names round trip`() {
        // The autocomplete picker stores canonical NAMES per metadata category.
        // Names contain commas, slashes, & and even apostrophes — all of which
        // used to be dangerous separators. \u0003 joins the list, then the whole
        // field is URL-encoded, so nothing inside a name can break the round trip.
        val filters = SearchFilters(
            fandomNames = listOf("Naruto (Anime & Manga)", "Harry Potter - J. K. Rowling"),
            characterNames = listOf("Uzumaki Naruto", "Hermione Granger"),
            relationshipNames = listOf("Uchiha Sasuke/Uzumaki Naruto", "Draco Malfoy & Harry Potter"),
            freeformNames = listOf("Slow Burn", "Hurt/Comfort, Angst with a Happy Ending"),
        )
        assertEquals(filters, SearchFilters.parse(filters.serialize()))
    }

    @Test
    fun `canonical tag names count as active filters`() {
        assert(SearchFilters(fandomNames = listOf("Naruto")).hasFilters)
        assert(SearchFilters(characterNames = listOf("Hermione")).hasFilters)
        assert(SearchFilters(relationshipNames = listOf("A/B")).hasFilters)
        assert(SearchFilters(freeformNames = listOf("Fluff")).hasFilters)
        assert(!SearchFilters().hasFilters)
    }
}
