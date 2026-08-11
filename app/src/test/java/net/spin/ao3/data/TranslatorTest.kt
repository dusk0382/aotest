package net.spin.ao3.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TranslatorTest {

    private fun translator() = Translator(DiskCache(File("/tmp/ao3_tr_test"), 0))

    @Test
    fun `parseGtx extrae los segmentos traducidos`() {
        val json = "[[[\"Hola mundo\",\"Hello world\",null,null,1]],null,\"es\",null,null,null,null,[]]"
        assertEquals("Hola mundo", translator().parseGtx(json))
    }

    @Test
    fun `parseGtx concatena varios segmentos`() {
        val json = "[[[\"Hola\",\"Hello\",null,null,10],[\" mundo\",\"world\",null,null,10]],null,\"es\"]"
        assertEquals("Hola mundo", translator().parseGtx(json))
    }

    @Test
    fun `parseGtx ignora segmentos nulos`() {
        val json = "[[[null,null,null,null,1],[\"Sí\",\"Yes\",null,null,1]],null,\"es\"]"
        assertEquals("Sí", translator().parseGtx(json))
    }

    @Test
    fun `extractBlocks toma los bloques de parrafo`() {
        val html = "<p>Primero.</p><p>Segundo.</p>"
        assertEquals(listOf("Primero.", "Segundo."), Translator.extractBlocks(html))
    }

    @Test
    fun `extractBlocks ignora vacios y br`() {
        val html = "<p>   </p><p>Texto.</p><br>"
        assertEquals(listOf("Texto."), Translator.extractBlocks(html))
    }

    @Test
    fun `extractBlocks desenvuelve un unico div contenedor`() {
        val html = "<div><p>Uno.</p><p>Dos.</p></div>"
        assertEquals(listOf("Uno.", "Dos."), Translator.extractBlocks(html))
    }

    @Test
    fun `rebuildHtml genera parrafos escapados`() {
        val out = Translator.rebuildHtml(listOf("A & B", "C <D>"))
        assertEquals("<p>A &amp; B</p>\n<p>C &lt;D&gt;</p>", out)
    }

    @Test
    fun `escapeHtml escapa especiales`() {
        assertEquals("&lt;3 &amp; &quot;hola&quot;", Translator.escapeHtml("<3 & \"hola\""))
    }
}
