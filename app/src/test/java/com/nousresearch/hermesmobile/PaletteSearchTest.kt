package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.HermesSession
import com.nousresearch.hermesmobile.data.PaletteSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteSearchTest {
    @Test fun prefixBeatsWordStartBeatsSubstring() {
        val prefix = PaletteSearch.score("Memoria", "mem")
        val word = PaletteSearch.score("Ajustes de memoria", "mem")
        val inside = PaletteSearch.score("Rememorar", "mem")
        assertTrue(prefix > word)
        assertTrue(word > inside)
        assertTrue(inside > 0)
    }

    @Test fun ignoresCaseAndAccents() {
        assertEquals(4, PaletteSearch.score("Configuración", "CONFIGURACION"))
        assertEquals(4, PaletteSearch.score("Apariencia", "apariéncia"))
    }

    @Test fun everyWordMustAppearInLabelOrKeywords() {
        assertEquals(0, PaletteSearch.score("Server settings", "server voice"))
        assertEquals(1, PaletteSearch.score("Voice", "voice speech", keywords = "tts speech"))
    }

    @Test fun blankQueryMatchesEverything() {
        assertEquals(1, PaletteSearch.score("Anything", "  "))
    }

    @Test fun sessionsRankByScoreThenRecency() {
        val old = HermesSession("a", title = "Deploy script", lastActive = 1)
        val new = HermesSession("b", title = "Deploy notes", lastActive = 2)
        val word = HermesSession("c", title = "Fix deploy", lastActive = 3)
        val preview = HermesSession("d", title = "Misc", preview = "we talked about deploy", lastActive = 4)
        val none = HermesSession("e", title = "Other", lastActive = 5)
        assertEquals(listOf("b", "a", "c", "d"), PaletteSearch.sessions(listOf(old, new, word, preview, none), "deploy").map { it.id })
    }

    @Test fun mergeKeepsLocalFirstWithoutDuplicates() {
        val local = listOf(HermesSession("a", profile = "p"), HermesSession("b", profile = "p"))
        val remote = listOf(HermesSession("b", profile = "p"), HermesSession("b", profile = "q"), HermesSession("c", profile = "p"))
        assertEquals(listOf("p:a", "p:b", "q:b", "p:c"), PaletteSearch.mergeSessions(local, remote).map { "${it.profile}:${it.id}" })
    }


    @Test fun mergeDropsServerHitsInSerializedToolCalls() {
        val remote = listOf(
            HermesSession("a", preview = "[{\"id\": \"call_00_bzwrn\", \"call_id\": \"x\"}]"),
            HermesSession("b", preview = "...{\"output\": \"bash: cloudflared: not found\"}"),
            HermesSession("c", preview = "[OUT-OF-BAND USER MESSAGE] instala esto"),
            HermesSession("d", preview = "recuérdame que me inscriban")
        )
        assertEquals(listOf("c", "d"), PaletteSearch.mergeSessions(emptyList(), remote).map { it.id })
    }
}
