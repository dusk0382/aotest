package net.spin.ao3.data

import net.spin.ao3.data.model.TagSuggestion
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
        assertTrue(names.any { it.name == "Naruto (Anime & Manga)" })
        assertEquals(names.size, names.map { it.name }.distinct().size)
    }

    @Test
    fun `character suggestions parse`() {
        val names = Ao3Parser.parseAutocomplete(snapshot("autocomplete_character.json"))
        assertTrue(names.any { it.name == "Uzumaki Naruto" })
        assertTrue(names.any { it.name == "Tsunade (Naruto)" })
    }

    @Test
    fun `relationship suggestions keep the slash syntax`() {
        val names = Ao3Parser.parseAutocomplete(snapshot("autocomplete_relationship.json"))
        assertTrue(names.any { it.name.contains("/") || it.name.contains(" & ") })
        assertTrue(names.any { it.name == "Uchiha Sasuke/Uzumaki Naruto" })
    }

    @Test
    fun `freeform suggestions parse`() {
        val names = Ao3Parser.parseAutocomplete(snapshot("autocomplete_freeform.json"))
        assertTrue(names.any { it.name.contains("Naruto") })
    }

    @Test
    fun `id falls back to name when blank`() {
        val names = Ao3Parser.parseAutocomplete(
            """[{"id":"","name":"Real Tag"},{"id":"x","name":"  "}]""",
        )
        assertEquals(1, names.size)
        assertEquals("Real Tag", names[0].name)
        assertEquals("Real Tag", names[0].id)
    }

    @Test
    fun `malformed json degrades to empty list`() {
        assertEquals(emptyList<TagSuggestion>(), Ao3Parser.parseAutocomplete("not json"))
        assertEquals(emptyList<TagSuggestion>(), Ao3Parser.parseAutocomplete(""))
        assertEquals(emptyList<TagSuggestion>(), Ao3Parser.parseAutocomplete("{\"a\":1}"))
        assertEquals(emptyList<TagSuggestion>(), Ao3Parser.parseAutocomplete("[]"))
    }
}
