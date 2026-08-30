package net.spin.ao3.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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

    // ---- Backend OpenAI-compatible ----

    @Test
    fun `parseOpenAi extrae el contenido de choices`() {
        val json = """{"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"Hola mundo."}}]}"""
        assertEquals("Hola mundo.", translator().parseOpenAi(json))
    }

    @Test
    fun `parseOpenAi recorta espacios del contenido`() {
        val json = """{"choices":[{"message":{"content":"  Traducción con salto\n  "}}]}"""
        assertEquals("Traducción con salto", translator().parseOpenAi(json))
    }

    @Test
    fun `parseOpenAi rechaza respuesta sin choices`() {
        try {
            translator().parseOpenAi("""{"object":"chat.completion"}""")
            fail("debió lanzar por respuesta sin choices")
        } catch (e: java.io.IOException) {
            // esperado: mensaje accionable, no un JSONException crudo
        }
    }

    @Test
    fun `aiErrorFromBody extrae el mensaje del error OpenAI`() {
        val body = """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}"""
        assertEquals("Incorrect API key provided", translator().aiErrorFromBody(body))
    }

    @Test
    fun `aiErrorFromBody devuelve null con cuerpo no JSON`() {
        assertEquals(null, translator().aiErrorFromBody("<html>502 Bad Gateway</html>"))
    }

    // ---- Backend clients5 (dict-chrome-ex) ----

    @Test
    fun `parseClients5 extrae el par traducción-idioma (forma verificada en vivo)`() {
        val json = """[["Hola mundo, esta es una prueba.","en"]]"""
        assertEquals("Hola mundo, esta es una prueba.", translator().parseClients5(json))
    }

    @Test
    fun `parseClients5 conserva los saltos dobles entre bloques`() {
        val json = """[["Uno.\n\nDos.\n\nTres.","en"]]"""
        assertEquals("Uno.\n\nDos.\n\nTres.", translator().parseClients5(json))
    }

    @Test
    fun `parseClients5 acepta strings planos`() {
        assertEquals("Uno.Dos.", translator().parseClients5("""["Uno.","Dos."]"""))
    }

    @Test
    fun `parseClients5 acepta la forma anidada estilo gtx`() {
        val json = """[[["Hola mundo","Hello world",null,null,10]],null,"en"]"""
        assertEquals("Hola mundo", translator().parseClients5(json))
    }

    @Test
    fun `parseClients5 rechaza JSON desconocido`() {
        try {
            translator().parseClients5("""{"raro":1}""")
            fail("debió lanzar por JSON desconocido")
        } catch (e: java.io.IOException) {
            // esperado
        }
    }

    @Test
    fun `config IA sin configurar se detecta`() {
        val empty = Translator.Config(provider = Translator.Config.Provider.AI)
        assertEquals(false, empty.aiConfigured)
        val full = Translator.Config(
            provider = Translator.Config.Provider.AI,
            aiBaseUrl = "https://api.openai.com/v1",
            aiApiKey = "sk-test",
            aiModel = "gpt-4o-mini",
        )
        assertEquals(true, full.aiConfigured)
    }

    @Test
    fun `translateChapterHtml sin IA configurada lanza error accionable`() = kotlinx.coroutines.runBlocking {
        val t = Translator(
            DiskCache(File("/tmp/ao3_tr_test"), 0),
        ) { Translator.Config(provider = Translator.Config.Provider.AI) }
        try {
            t.translateChapterHtml("k", "<p>Hola.</p>", "en")
            fail("debió lanzar por IA sin configurar")
        } catch (e: java.io.IOException) {
            assert(e.message!!.contains("Ajustes → Traducción")) { "mensaje sin pista: $e" }
        }
    }
}
