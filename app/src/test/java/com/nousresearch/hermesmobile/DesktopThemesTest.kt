package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.ui.theme.DesktopThemes
import com.nousresearch.hermesmobile.ui.theme.ThemePalette
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected values come from running Desktop's own `skinToDesktopTheme` (apps/desktop/src/themes/skin.ts) in Node. */
class DesktopThemesTest {
    private fun hex(c: Long) = "#%06x".format(c and 0xFFFFFF)
    private fun ThemePalette.asMap() = mapOf(
        "background" to background, "foreground" to foreground, "card" to card, "muted" to muted,
        "mutedForeground" to mutedForeground, "popover" to popover, "primary" to primary,
        "primaryForeground" to primaryForeground, "secondary" to secondary, "accent" to accent, "border" to border,
        "destructive" to destructive, "sidebarBackground" to sidebarBackground, "userBubble" to userBubble,
        "userBubbleBorder" to userBubbleBorder
    ).mapValues { hex(it.value) }

    private fun assertDesktop(expected: String, actual: ThemePalette) {
        val want = JSONObject(expected)
        actual.asMap().forEach { (key, value) -> assertEquals(key, want.getString(key), value) }
    }

    private fun skin(name: String, vararg colors: Pair<String, String>) =
        JSONObject().put("name", name).put("colors", JSONObject(colors.toMap()))

    @Test
    fun convertsSkinsExactlyLikeDesktop() {
        assertDesktop("""{"background":"#20242c","foreground":"#d8dee9","card":"#272b34","muted":"#2b2f37","mutedForeground":"#858a94","popover":"#2f333b","primary":"#7e9abd","primaryForeground":"#161616","secondary":"#3a4555","accent":"#313946","border":"#3d424a","destructive":"#bf616a","sidebarBackground":"#242830","userBubble":"#313946","userBubbleBorder":"#3d424a"}""",
            DesktopThemes.fromServerSkin(skin("custom", "background" to "#20242c", "ui_text" to "#d8dee9", "ui_accent" to "#5e81ac", "ui_error" to "#bf616a")).light)
        // A light skin whose accent fails contrast: Desktop darkens it in 0.2 rungs.
        assertDesktop("""{"background":"#fafafa","foreground":"#333333","card":"#f5f5f5","muted":"#f2f2f2","mutedForeground":"#8d8d8d","popover":"#f0f0f0","primary":"#665300","primaryForeground":"#ffffff","secondary":"#e5e3d7","accent":"#e8e6dc","border":"#dedede","destructive":"#e25563","sidebarBackground":"#f8f8f8","userBubble":"#e8e6dc","userBubbleBorder":"#dedede"}""",
            DesktopThemes.fromServerSkin(skin("light", "status_bar_bg" to "#fafafa", "banner_text" to "#333333", "ui_accent" to "#ffd000")).light)
    }

    @Test
    fun classicHermesMatchesDesktop() {
        val classic = DesktopThemes.builtIn.getValue("classic")
        assertDesktop("""{"background":"#1a1a2e","foreground":"#fff8dc","card":"#232335","muted":"#282738","mutedForeground":"#b8860b","popover":"#2c2c3c","primary":"#ffbf00","primaryForeground":"#161616","secondary":"#5a4821","accent":"#433826","border":"#cd7f32","destructive":"#ef5350","sidebarBackground":"#1f1e31","userBubble":"#433826","userBubbleBorder":"#cd7f32"}""",
            classic.paletteFor(darkMode = true))
        assertFalse(classic.paletteFor(darkMode = false).isDark)
    }

    @Test
    fun followsDesktopNamingRules() {
        // Stock config ships `display.skin: default`, which Desktop reads as "no pick": Nous.
        for (name in listOf("default", "gold", "nous-light", "")) assertEquals("nous", DesktopThemes.fromServerSkin(skin(name)).name)
        assertEquals("nous", DesktopThemes.fromServerSkin(null).name)
        // A built-in name keeps Desktop's palette, whatever colors the skin carries.
        assertEquals(DesktopThemes.builtIn.getValue("catppuccin"), DesktopThemes.fromServerSkin(skin("catppuccin", "background" to "#000000")))
        // A pinned choice wins over the server; an unknown pin falls back to Nous.
        assertEquals("ember", DesktopThemes.resolve("ember", skin("catppuccin")).name)
        assertEquals("nous", DesktopThemes.resolve("gone", null).name)
        assertEquals("catppuccin", DesktopThemes.resolve("", skin("catppuccin")).name)
    }

    @Test
    fun shipsDesktopBuiltInsInOrder() {
        val names = DesktopThemes.builtIn.keys.toList()
        assertEquals(listOf("nous", "github", "catppuccin", "everforest", "solarized", "nous-alt", "classic",
            "midnight", "ember", "mono", "slate", "cyberpunk"), names)
        val nous = DesktopThemes.nous
        assertEquals("#0d1117", hex(nous.paletteFor(true).background))
        assertEquals("#0053fd", hex(nous.paletteFor(false).primary))
        assertTrue(nous.paletteFor(true).isDark)
        // Dark-only themes have one palette and paint it in either mode.
        assertNull(DesktopThemes.builtIn.getValue("midnight").dark)
        assertTrue(DesktopThemes.builtIn.getValue("midnight").paletteFor(false).isDark)
    }
}
