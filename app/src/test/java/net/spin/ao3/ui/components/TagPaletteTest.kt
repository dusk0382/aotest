package net.spin.ao3.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contraste WCAG de la paleta sólida de ratings/advertencias. JVM puro: evalúa
 * el efecto visible de cada par (no comprueba sólo que el literal existe), así
 * una futura combinación ilegible hace fallar el test.
 */
class TagPaletteTest {

    private fun srgb(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val (hi, lo) = if (la >= lb) la to lb else lb to la
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun blend(surface: Long, accent: Long, amount: Float): Long {
        val ch = { shift: Int ->
            val s = ((surface shr shift) and 0xFF).toInt()
            val a = ((accent shr shift) and 0xFF).toInt()
            (s + (a - s) * amount).toInt().coerceIn(0, 255)
        }
        return 0xFF000000L or (ch(16).toLong() shl 16) or (ch(8).toLong() shl 8) or ch(0).toLong()
    }

    private val lightSurface = 0xFFF4F6F8
    private val darkSurface = 0xFF111820

    private val ratingKeys = listOf(
        "general-audiences",
        "teen-and-up-audiences",
        "mature",
        "explicit",
        "not-rated",
        null,
        "rating:unknown",
    )

    @Test
    fun `pares solidos superan 4_5 a 1 en claro y oscuro`() {
        for (key in ratingKeys) {
            for (light in listOf(true, false)) {
                val (container, text) = ratingSolidHex(key, light)
                val ratio = contrast(container, text)
                assertTrue(
                    "rating=$key light=$light container=${container.toString(16)} " +
                        "text=${text.toString(16)} ratio=$ratio",
                    ratio >= 4.5,
                )
            }
        }
        for (light in listOf(true, false)) {
            val (container, text) = warningSolidHex(light)
            val ratio = contrast(container, text)
            assertTrue("warning light=$light ratio=$ratio", ratio >= 4.5)
        }
    }

    @Test
    fun `texto de acento sobre superficie mezclada supera 4_5 a 1`() {
        for (key in ratingKeys) {
            for (light in listOf(true, false)) {
                val surface = if (light) lightSurface else darkSurface
                val accent = ratingTextHex(key, light)
                val bg = blend(surface, accent, 0.18f)
                val ratio = contrast(bg, accent)
                assertTrue(
                    "rating=$key light=$light bg=${bg.toString(16)} " +
                        "accent=${accent.toString(16)} ratio=$ratio",
                    ratio >= 4.5,
                )
            }
        }
    }
}
