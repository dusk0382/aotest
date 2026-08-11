package net.spin.ao3.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the reader progress bug: WebView.evaluateJavascript
 * returns the JS result JSON-encoded, so a String comes back quoted
 * ("0.4321"). The old `r?.toFloatOrNull()` always failed on the quotes and
 * every ratio was saved as 0 -> the reader never restored scroll and
 * "Continuar leyendo" showed 0% forever.
 */
class ReaderRatioTest {

    @Test
    fun `quoted ratio parses`() {
        assertEquals(0.4321f, parseJsRatio("\"0.4321\""), 0.0001f)
    }

    @Test
    fun `quoted integer parses`() {
        assertEquals(0f, parseJsRatio("\"0\""), 0f)
        assertEquals(1f, parseJsRatio("\"1\""), 0f)
    }

    @Test
    fun `unquoted ratio parses`() {
        assertEquals(0.5f, parseJsRatio("0.5"), 0.0001f)
    }

    @Test
    fun `null and garbage fall back to zero`() {
        assertEquals(0f, parseJsRatio("\"null\""), 0f)
        assertEquals(0f, parseJsRatio("null"), 0f)
        assertEquals(0f, parseJsRatio(null), 0f)
        assertEquals(0f, parseJsRatio("NaN"), 0f)
    }
}
