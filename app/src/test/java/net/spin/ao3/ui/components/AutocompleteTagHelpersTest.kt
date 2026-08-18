package net.spin.ao3.ui.components

import net.spin.ao3.data.Ao3Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Helpers that back the CO3-style tag autocomplete chips (comma-separated model). */
class AutocompleteTagHelpersTest {

    @Test
    fun `committedTagSegments keeps only segments followed by a comma`() {
        assertEquals(emptyList<String>(), committedTagSegments(""))
        assertEquals(emptyList<String>(), committedTagSegments("Angst"))
        assertEquals(listOf("Angst"), committedTagSegments("Angst, "))
        assertEquals(listOf("Angst", "Fluff"), committedTagSegments("Angst, Fluff, Hurt/Comfort"))
    }

    @Test
    fun `activeTagSegment is the last segment being typed`() {
        assertEquals("", activeTagSegment(""))
        assertEquals("Angst", activeTagSegment("Angst"))
        assertEquals("Fluff", activeTagSegment("Angst, Fluff"))
        assertEquals("", activeTagSegment("Angst, "))
        assertEquals("Hurt/Comfort", activeTagSegment("Angst, Fluff, Hurt/Comfort"))
    }

    @Test
    fun `appendCommittedTag adds to the committed part and leaves a trailing comma`() {
        assertEquals("Angst, ", appendCommittedTag("", "Angst"))
        assertEquals("Angst, Fluff, ", appendCommittedTag("Angst, ", "Fluff"))
        assertEquals("Angst, Fluff, Hurt/Comfort, ", appendCommittedTag("Angst, Fluff, ", "Hurt/Comfort"))
        // Typed-but-uncommitted text is replaced, not kept.
        assertEquals("Angst, Fluff, ", appendCommittedTag("Angst, dr", "Fluff"))
    }

    @Test
    fun `replaceActiveSegment keeps chips and swaps only the last segment`() {
        assertEquals("", replaceActiveSegment("", ""))
        assertEquals("Fl", replaceActiveSegment("", "Fl"))
        assertEquals("Angst, Fl", replaceActiveSegment("Angst, ", "Fl"))
        assertEquals("Angst, ", replaceActiveSegment("Angst, ", ""))
        assertEquals("Angst, Fluff, H", replaceActiveSegment("Angst, Fluff, Hurt", "H"))
    }

    @Test
    fun `removeCommittedTag removes one chip and keeps the active segment`() {
        assertEquals("", removeCommittedTag("Angst, ", 0))
        assertEquals("Fluff, ", removeCommittedTag("Angst, Fluff, ", 0))
        assertEquals("Angst, ", removeCommittedTag("Angst, Fluff, ", 1))
        assertEquals("Fluff", removeCommittedTag("Angst, Fluff", 0))
        // "Fluff" is the ACTIVE segment (not a chip): only index 0 exists.
        assertEquals("Angst, Fluff", removeCommittedTag("Angst, Fluff", 1))
        // No committed chips: index is out of range, input unchanged.
        assertEquals("Angst", removeCommittedTag("Angst", 0))
        assertEquals("Angst, Fluff", removeCommittedTag("Angst, Fluff", 5))
    }

    @Test
    fun `round trip preserves the committed tags`() {
        var s = ""
        s = appendCommittedTag(s, "Angst")
        assertEquals("Angst, ", s)
        s = replaceActiveSegment(s, "Fluff")
        assertEquals("Angst, Fluff", s)
        // Commit the active segment (what the keyboard submit does).
        s = appendCommittedTag(s, "Fluff")
        assertEquals("Angst, Fluff, ", s)
        s = appendCommittedTag(s, "Hurt/Comfort")
        assertEquals("Angst, Fluff, Hurt/Comfort, ", s)
        assertEquals(listOf("Angst", "Fluff", "Hurt/Comfort"), committedTagSegments(s))
        s = removeCommittedTag(s, 1)
        assertEquals("Angst, Hurt/Comfort, ", s)
    }
}

/** Parsing of AO3's `/autocomplete/{type}` JSON response (via [Ao3Parser]). */
class AutocompleteJsonTest {

    @Test
    fun `parses id and name pairs`() {
        val json = """[{"id":"Harry Potter - J. K. Rowling","name":"Harry Potter - J. K. Rowling"},""" +
            """{"id":"Hermione Granger","name":"Hermione Granger"}]"""
        val parsed = Ao3Parser.parseAutocomplete(json)
        assertEquals(2, parsed.size)
        assertEquals("Hermione Granger", parsed[1].name)
        assertEquals("Hermione Granger", parsed[1].id)
    }

    @Test
    fun `id may differ from name`() {
        val json = """[{"id":"slug-key","name":"Display Name"}]"""
        val parsed = Ao3Parser.parseAutocomplete(json)
        assertEquals("slug-key", parsed[0].id)
        assertEquals("Display Name", parsed[0].name)
    }

    @Test
    fun `blank names are dropped`() {
        val json = """[{"id":"x","name":"  "},{"id":"y","name":"Real Tag"}]"""
        val parsed = Ao3Parser.parseAutocomplete(json)
        assertEquals(1, parsed.size)
        assertEquals("Real Tag", parsed[0].name)
    }

    @Test
    fun `malformed input returns empty list`() {
        assertTrue(Ao3Parser.parseAutocomplete("").isEmpty())
        assertTrue(Ao3Parser.parseAutocomplete("not json").isEmpty())
        assertTrue(Ao3Parser.parseAutocomplete("""{"id":1}""").isEmpty())
    }

    @Test
    fun `empty array parses to empty list`() {
        assertTrue(Ao3Parser.parseAutocomplete("[]").isEmpty())
    }
}
