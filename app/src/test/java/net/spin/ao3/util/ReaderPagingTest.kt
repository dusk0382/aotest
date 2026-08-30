package net.spin.ao3.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPagingTest {

    private val sampleHtml = """
        <p>Primer párrafo con <strong>negrita</strong> y <em>cursiva</em>.</p>
        <p>Segundo párrafo, bastante más largo para llenar presupuesto de página en la prueba.</p>
        <blockquote><p>Cita de alguien.</p></blockquote>
        <hr>
        <p>Último párrafo.</p>
    """.trimIndent()

    @Test
    fun `htmlToLines conserva párrafos, estilos inline y separadores`() {
        val lines = htmlToLines(sampleHtml)
        assertEquals(5, lines.size)

        val first = lines[0]
        assertTrue("negrita marcada", first.segments.any { it.bold })
        assertTrue("cursiva marcada", first.segments.any { it.italic })

        assertEquals(LineKind.QUOTE, lines[2].kind)
        assertEquals("Cita de alguien.", lines[2].segments.joinToString("") { it.text }.trim())
        assertEquals(LineKind.SEPARATOR, lines[3].kind)
        assertEquals(LineKind.PARAGRAPH, lines[4].kind)
    }

    @Test
    fun `htmlToLines ignora párrafos vacíos`() {
        val lines = htmlToLines("<p>Uno</p><p>  </p><p>Dos</p>")
        assertEquals(2, lines.size)
        assertEquals("Uno", lines[0].segments.joinToString("") { it.text }.trim())
        assertEquals("Dos", lines[1].segments.joinToString("") { it.text }.trim())
    }

    @Test
    fun `packPages respeta el presupuesto y no parte líneas`() {
        val lines = htmlToLines(sampleHtml)
        val pages = packPages(lines, targetChars = 40)
        assertTrue("al menos dos páginas", pages.size >= 2)

        val flattened = pages.flatten()
        assertEquals("todas las líneas se conservan", lines.size, flattened.size)

        // Ninguna línea queda partida: los segmentos de una línea están juntos.
        pages.forEach { page ->
            page.forEach { line ->
                assertTrue(line.segments.isNotEmpty() || line.kind == LineKind.SEPARATOR)
            }
        }
        // El presupuesto se respeta salvo cuando una sola línea lo supera.
        pages.forEach { page ->
            val len = page.sumOf { l -> l.segments.sumOf { it.text.length } }
            assertTrue(
                "página dentro de presupuesto (o línea única mayor)",
                len <= 40 || page.size == 1,
            )
        }
    }

    @Test
    fun `packPages devuelve una sola página cuando cabe todo`() {
        val lines = htmlToLines(sampleHtml)
        assertEquals(1, packPages(lines, 100_000).size)
    }

    @Test
    fun `packPages con entrada vacía devuelve lista vacía`() {
        assertEquals(emptyList<List<Line>>(), packPages(emptyList(), 1000))
    }

// ---- Filas visuales: partir párrafos entre páginas (páginas llenas) ----


@Test
fun `sliceSegments corta conservando estilos`() {
    val segs = listOf(
        Segment("Hola ", bold = true),
        Segment("mundo"),
        Segment(" cruel", italic = true),
    )
    // "Hola mundo cruel": [0,5) mantiene negrita; [3,14) cruza segmentos.
    assertEquals(listOf(Segment("Hola", bold = true)), sliceSegments(segs, 0, 4))
    val medio = sliceSegments(segs, 3, 14)
    assertEquals(listOf(Segment("a ", bold = true), Segment("mundo", bold = false), Segment(" cru", italic = true)), medio)
    // Rango completo devuelve los segmentos intactos (misma instancia).
    assertTrue(sliceSegments(segs, 0, 16) === segs)
}

@Test
fun `un bloque partido conserva el texto completo al re-ensamblar`() {
    val lines = htmlToLines("<p>AAAA BBBB CCCC DDDD</p>")
    val block = lines.single()
    val parts = listOf(
        Line(sliceSegments(block.segments, 0, 4), paragraphStart = true, paragraphEnd = false),
        Line(sliceSegments(block.segments, 5, 14), paragraphStart = false, paragraphEnd = false),
        Line(sliceSegments(block.segments, 15, 19), paragraphStart = false, paragraphEnd = true),
    )
    // La fila de continuación se une con "\n" (no "\n\n"): texto continuo.
    assertEquals("AAAA\nBBBB CCCC\nDDDD", pagePlainText(parts))
    assertEquals("AAAA BBBB CCCC DDDD", pagePlainText(parts).replace("\n", " ").replace("  ", " "))
}

@Test
fun `los flags de bloque controlan comillas y viñetas por fila`() {
    val cita = listOf(
        Line(listOf(Segment("inicio de")), LineKind.QUOTE, paragraphStart = true, paragraphEnd = false),
        Line(listOf(Segment("continuación")), LineKind.QUOTE, paragraphStart = false, paragraphEnd = true),
    )
    assertEquals("“inicio de\ncontinuación”", pagePlainText(cita))

    val lista = listOf(
        Line(listOf(Segment("primero")), LineKind.LIST, paragraphStart = true, paragraphEnd = false),
        Line(listOf(Segment("segundo")), LineKind.LIST, paragraphStart = false, paragraphEnd = true),
    )
    assertEquals("•  primero\nsegundo", pagePlainText(lista))
}
}
