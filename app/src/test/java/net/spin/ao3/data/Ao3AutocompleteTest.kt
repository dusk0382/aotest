package net.spin.ao3.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [Ao3Parser.parseAutocomplete] against real responses from AO3's
 * native /autocomplete endpoint (saved as snapshots). The endpoint returns an
 * array of `{id, name}` objects where both fields hold the canonical name.
 */
class Ao3AutocompleteTest {

    private fun snapshot(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("ao3/$name")) { "missing ao3/$name" }
            .readText()

    @Test
    fun `fandom suggestions parse to canonical names`() {
        val names = Ao3Parser.parseAutocomplete(snapshot("autocomplete_fandom.json"))
        assertTrue(names.isNotEmpty())
        // The id IS the canonical name AO3 needs in /tags/{name}/works.
        assertTrue(names.contains("Naruto (Anime & Manga)"))
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `character suggestions parse`() {
        val names = Ao3Parser.parseAutocomplete(snapshot("autocomplete_character.json"))
        assertTrue(names.contains("Uzumaki Naruto"))
        assertTrue(names.contains("Tsunade (Naruto)"))
    }

    @Test
    fun `relationship suggestions keep the slash syntax`() {
        val names = Ao3Parser.parseAutocomplete(snapshot("autocomplete_relationship.json"))
        assertTrue(names.any { it.contains("/") || it.contains(" & ") })
        assertTrue(names.contains("Uchiha Sasuke/Uzumaki Naruto"))
    }

    @Test
    fun `freeform suggestions parse`() {
        val names = Ao3Parser.parseAutocomplete(snapshot("autocomplete_freeform.json"))
        assertTrue(names.any { it.contains("Naruto") })
    }

    @Test
    fun `malformed json degrades to empty list`() {
        assertEquals(emptyList<String>(), Ao3Parser.parseAutocomplete("not json"))
        assertEquals(emptyList<String>(), Ao3Parser.parseAutocomplete(""))
        assertEquals(emptyList<String>(), Ao3Parser.parseAutocomplete("{\"a\":1}"))
        assertEquals(emptyList<String>(), Ao3Parser.parseAutocomplete("[]"))
    }
}
