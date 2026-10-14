package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.assist.SpeechText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test
    fun cleansMarkdownForSpeech() {
        val md = """
            ## Tiempo hoy
            Hace **sol** en [Madrid](https://aemet.es/madrid), ver https://example.com.
            - Máxima: 24 °C
            1. Mínima: 12 °C
            ```bash
            curl wttr.in
            ```
            > Fuente: `AEMET`
        """.trimIndent()
        val spoken = SpeechText.clean(md)
        assertEquals("Tiempo hoy\nHace sol en Madrid, ver.\nMáxima: 24 °C\nMínima: 12 °C\nFuente: AEMET", spoken)
    }

    @Test
    fun streamsWholeSentencesOnly() {
        val s = SpeechText.Streamer()
        assertEquals(emptyList<String>(), s.push("Hola, soy"))
        assertEquals(listOf("Hola, soy Hermes."), s.push("Hola, soy Hermes. Hoy hace"))
        assertEquals(emptyList<String>(), s.push("Hola, soy Hermes. Hoy hace sol"))
        assertEquals(listOf("Hoy hace sol en Madrid."), s.finish("Hola, soy Hermes. Hoy hace sol en Madrid."))
    }

    @Test
    fun waitsForCodeFencesToClose() {
        val s = SpeechText.Streamer()
        assertEquals(listOf("Ejecuta esto:"), s.push("Ejecuta esto:\n```\nls -la. Y más\n"))
        // Still inside the fence: nothing new, even past a period.
        assertEquals(emptyList<String>(), s.push("Ejecuta esto:\n```\nls -la. Y más\nrm x. \n"))
        val done = "Ejecuta esto:\n```\nls -la. Y más\nrm x. \n```\nListo. "
        assertEquals(listOf("Listo."), s.push(done))
        assertTrue(s.finish(done).isEmpty())
    }

    @Test
    fun aReplacedStreamIsNotRepeated() {
        val s = SpeechText.Streamer()
        s.push("Primera frase. Segunda")
        assertTrue(s.finish("Otra respuesta distinta.").isEmpty())
        assertEquals(listOf("Una.", "Dos."), SpeechText.Streamer().finish("Una. Dos."))
    }
}
