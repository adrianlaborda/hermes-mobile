package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.LocalSlash
import com.nousresearch.hermesmobile.data.SlashCommands
import com.nousresearch.hermesmobile.data.SlashCompletion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandsTest {
    @Test
    fun queryOnlyForSingleLineSlashDrafts() {
        assertEquals("/mo", SlashCommands.query("/mo"))
        assertNull(SlashCommands.query("hello /mo"))
        assertNull(SlashCommands.query("/model\nmore"))
        assertNull(SlashCommands.query(""))
    }

    @Test
    fun localCommandsAreHandledByTheApp() {
        assertEquals(LocalSlash.NEW, SlashCommands.local("/new"))
        assertEquals(LocalSlash.MODEL, SlashCommands.local("/model"))
        // `/model x` carries an argument: the gateway switches it directly.
        assertNull(SlashCommands.local("/model gpt-5"))
        assertEquals(LocalSlash.RETRY, SlashCommands.local("/retry"))
        assertEquals(LocalSlash.COMPRESS, SlashCommands.local("/compact"))
        assertEquals(LocalSlash.BRANCH, SlashCommands.local("/branch"))
        assertNull(SlashCommands.local("/status"))
        assertTrue(SlashCommands.rewinds("/undo 2"))
        assertFalse(SlashCommands.rewinds("/usage"))
    }

    @Test
    fun parsesCompletionsAndReplaceFrom() {
        val json = JSONObject("""{"items":[{"text":"/model","display":"/model","meta":"Switch model","kind":"command"},
            {"text":"/pdf","meta":"Skill","kind":"skill"},{"text":"/model"}],"replace_from":1}""")
        val (items, from) = SlashCommands.parseCompletions(json)
        assertEquals(1, from)
        assertEquals(listOf("/model", "/pdf"), items.map { it.text })
        assertTrue(items[1].skill)
        assertEquals("/pdf", items[1].display)
    }

    @Test
    fun appliesCommandAndArgumentCompletionsLikeDesktop() {
        assertEquals("/model ", SlashCommands.apply("/mo", SlashCompletion("/model", "/model", "", false), 1))
        assertEquals("/personality alice ", SlashCommands.apply("/personality al", SlashCompletion("alice", "alice", "", false), 13))
        assertEquals("/help ", SlashCommands.apply("/he", SlashCompletion("help", "help", "", false), 1))
    }

    @Test
    fun parsesDispatchResults() {
        val send = SlashCommands.parseResult(JSONObject("""{"type":"send","message":"again"}"""))
        assertEquals("send", send.type)
        assertEquals("again", send.message)
        val exec = SlashCommands.parseResult(JSONObject("""{"output":"ok","warning":"careful"}"""))
        assertEquals("exec", exec.type)
        assertEquals("ok", exec.output)
        assertEquals("careful", exec.notice)
    }

    private val catalog = SlashCommands.parseCatalog(JSONObject("""{
        "pairs":[["/new","Start a new session"],["/model","Switch model"],["/compress","Summarize older turns"],["/pdf","Read PDF files"],["/review","Review code"]],
        "canon":{"/new":"/new","/reset":"/new","/model":"/model","/compact":"/compress"},
        "categories":[{"name":"Session","pairs":[["/new","Start a new session"],["/compress","Summarize older turns"]]},
                      {"name":"Configuration","pairs":[["/model","Switch model"]]}],
        "skills":{"/pdf":{"usage":2,"origin":"bundled"},"/review":{"usage":9,"origin":"hub"}}}"""))

    @Test
    fun parsesCatalogByCategoryWithSkillsLast() {
        assertEquals(listOf("/new", "/compress", "/model", "/pdf", "/review"), catalog.entries.map { it.name })
        assertEquals("Session", catalog.entries.first().category)
        assertTrue(catalog.entries.last().skill)
        assertEquals("Read PDF files", catalog.entries.first { it.name == "/pdf" }.description)
        assertEquals(mapOf("/reset" to "/new", "/compact" to "/compress"), catalog.aliases)
    }

    @Test
    fun bareSlashBrowsesEverythingGroupedWithMostUsedSkillsFirst() {
        val rows = SlashCommands.filter(catalog, "/")
        assertEquals(listOf("/new", "/compress", "/model", "/review", "/pdf"), rows.map { it.text })
        assertEquals(listOf("Session", "Session", "Configuration", SlashCommands.SECTION_SKILLS, SlashCommands.SECTION_SKILLS), rows.map { it.section })
    }

    @Test
    fun typingNarrowsByPrefixAliasSubstringAndDescription() {
        assertEquals(listOf("/model"), SlashCommands.filter(catalog, "/mo").map { it.text })
        // `/compact` is an alias of `/compress`.
        assertEquals(listOf("/compress"), SlashCommands.filter(catalog, "/compa").map { it.text })
        assertEquals("/new", SlashCommands.filter(catalog, "/rese").single().text)
        // Description words match too, after name hits.
        assertEquals(listOf("/pdf"), SlashCommands.filter(catalog, "/read").map { it.text })
        // In-order letters: `/mdl` still finds `/model`.
        assertEquals(listOf("/model"), SlashCommands.filter(catalog, "/mdl").map { it.text })
        assertTrue(SlashCommands.filter(catalog, "/zzz").isEmpty())
    }

    @Test
    fun onlyTheCommandTokenIsMatchedLocally() {
        assertTrue(SlashCommands.commandStage("/mo"))
        assertFalse(SlashCommands.commandStage("/model gpt"))
    }
}
