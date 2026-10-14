package com.nousresearch.hermesmobile.ui.theme

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Hermes Desktop's theme model, ported so the app paints what Desktop paints.
 *
 * Sources in hermes-agent: `apps/desktop/src/themes/{presets,skin,color,backend-sync}.ts` and
 * `apps/shared/src/{theme-presets,color}.ts`. Colors are ARGB longs; the math works in sRGB like Desktop.
 */
data class ThemePalette(
    val background: Long, val foreground: Long, val card: Long, val muted: Long, val mutedForeground: Long,
    val popover: Long, val primary: Long, val primaryForeground: Long, val secondary: Long, val accent: Long,
    val border: Long, val destructive: Long, val sidebarBackground: Long, val userBubble: Long, val userBubbleBorder: Long
) {
    /** Desktop paints `.dark` from the real background luminance. */
    val isDark: Boolean get() = ThemeColor.luminance(background) < 0.4

    companion object {
        fun of(
            background: Long, foreground: Long, card: Long, muted: Long, mutedForeground: Long, popover: Long,
            primary: Long, primaryForeground: Long, secondary: Long, accent: Long, border: Long, destructive: Long,
            sidebarBackground: Long, userBubble: Long, userBubbleBorder: Long
        ) = ThemePalette(background, foreground, card, muted, mutedForeground, popover, primary, primaryForeground,
            secondary, accent, border, destructive, sidebarBackground, userBubble, userBubbleBorder)
    }
}

/** A theme with a light palette and, for two-mode themes, a dark one. Single-palette themes ignore the mode. */
data class DesktopTheme(val name: String, val label: String, val light: ThemePalette, val dark: ThemePalette?) {
    fun paletteFor(darkMode: Boolean): ThemePalette = if (darkMode && dark != null) dark else light
}

/** Desktop's sRGB helpers (`apps/shared/src/color.ts`, `apps/desktop/src/themes/color.ts`). */
object ThemeColor {
    private fun ch(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()
    fun r(c: Long) = ch(c, 16); fun g(c: Long) = ch(c, 8); fun b(c: Long) = ch(c, 0)
    fun rgb(r: Int, g: Int, b: Int): Long =
        0xFF000000L or (r.coerceIn(0, 255).toLong() shl 16) or (g.coerceIn(0, 255).toLong() shl 8) or b.coerceIn(0, 255).toLong()

    /** `mix(a, b, t)`: sRGB lerp a → b. */
    fun mix(a: Long, b: Long, t: Double): Long = rgb(
        (r(a) + (r(b) - r(a)) * t).roundToInt(), (g(a) + (g(b) - g(a)) * t).roundToInt(), (b(a) + (b(b) - b(a)) * t).roundToInt()
    )

    /** Naive luminance for light/dark bucketing. */
    fun luminance(c: Long): Double = 0.2126 * r(c) / 255 + 0.7152 * g(c) / 255 + 0.0722 * b(c) / 255

    private fun channel(v: Int): Double { val n = v / 255.0; return if (n <= 0.03928) n / 12.92 else ((n + 0.055) / 1.055).pow(2.4) }
    fun relativeLuminance(c: Long): Double = 0.2126 * channel(r(c)) + 0.7152 * channel(g(c)) + 0.0722 * channel(b(c))
    fun contrast(a: Long, b: Long): Double {
        val la = relativeLuminance(a); val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Desktop's ink pair: near-black `#161616` or white, whichever measures better (ties go to white). */
    fun readableInk(bg: Long): Long {
        val dark = 0xFF161616L; val light = 0xFFFFFFFFL
        return if (contrast(bg, dark) > contrast(bg, light)) dark else light
    }

    /** Step-mix toward the pole opposite [bg] in 0.2 rungs until contrast clears [min]. */
    fun ensureContrast(color: Long, bg: Long, min: Double): Long {
        if (contrast(color, bg) >= min) return color
        val pole = if (relativeLuminance(bg) < 0.5) 0xFFFFFFFFL else 0xFF000000L
        var best = color
        var amount = 0.2
        while (amount <= 1.0001) {
            best = mix(color, pole, min(amount, 1.0))
            if (contrast(best, bg) >= min) return best
            amount += 0.2
        }
        return best
    }

    /** `#rgb`, `#rgba`, `#rrggbb` or `#rrggbbaa`, alpha flattened over [backdrop]; null otherwise. */
    fun parse(input: String?, backdrop: Long = 0xFF000000L): Long? {
        var clean = input?.trim()?.removePrefix("#") ?: return null
        if (clean.length == 3 || clean.length == 4) clean = clean.map { "$it$it" }.joinToString("")
        if (!Regex("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(clean)) return null
        val color = 0xFF000000L or clean.substring(0, 6).toLong(16)
        if (clean.length == 6) return color
        val alpha = clean.substring(6, 8).toInt(16) / 255.0
        return mix(backdrop, color, alpha)
    }
}

object DesktopThemes {
    /** Skin used when nothing is chosen or the server's skin is retired. */
    const val DEFAULT = "nous"
    /** Names that no longer have a skin of their own: stock config ships `display.skin: default`. */
    val RETIRED = setOf("nous-light", "default", "gold")

    private const val ACCENT_MIN_CONTRAST = 4.5

    /** Classic Hermes: the CLI's `default` skin, through the same converter a backend skin takes. */
    private val classic: DesktopTheme by lazy {
        fun palette(colors: Map<String, String>) = fromSkinColors("classic", JSONObject(colors))!!.light
        DesktopTheme("classic", "Classic Hermes",
            palette(mapOf("status_bar_bg" to "#F5F5F5", "banner_text" to "#2B2109", "ui_accent" to "#D89B04",
                "banner_border" to "#CD7F32", "banner_dim" to "#B8860B", "ui_error" to "#C62828", "completion_menu_bg" to "#F5F5F5")),
            palette(mapOf("status_bar_bg" to "#1a1a2e", "banner_text" to "#FFF8DC", "ui_accent" to "#FFBF00",
                "banner_border" to "#CD7F32", "banner_dim" to "#B8860B", "ui_error" to "#ef5350", "completion_menu_bg" to "#1a1a2e")))
    }

    /** Desktop's BUILTIN_THEMES, in its order. */
    val builtIn: Map<String, DesktopTheme> by lazy {
        val order = listOf("nous", "github", "catppuccin", "everforest", "solarized", "nous-alt", "classic",
            "midnight", "ember", "mono", "slate", "cyberpunk")
        val all = DESKTOP_PRESET_PALETTES + ("classic" to classic)
        linkedMapOf<String, DesktopTheme>().apply {
            order.forEach { name -> all[name]?.let { put(name, it) } }
            all.forEach { (name, theme) -> putIfAbsent(name, theme) }
        }
    }

    val nous: DesktopTheme get() = builtIn.getValue(DEFAULT)

    /**
     * The theme for a skin the server announced (`gateway.ready` / `skin.changed`): a built-in name keeps
     * Desktop's palette, a retired or unusable one is Nous, anything else is converted from its colors.
     */
    fun fromServerSkin(skin: JSONObject?): DesktopTheme {
        val name = skin?.optString("name").orEmpty().trim()
        if (name.isEmpty() || name in RETIRED) return nous
        builtIn[name]?.let { return it }
        return skin?.optJSONObject("colors")?.let { fromSkinColors(name, it) } ?: nous
    }

    /** Port of Desktop's `skinToDesktopTheme`: seed from a CLI-shaped skin, derive the rest by mixing. */
    fun fromSkinColors(name: String, colors: JSONObject): DesktopTheme? {
        fun pick(keys: List<String>, backdrop: Long): Long? = keys.firstNotNullOfOrNull { ThemeColor.parse(colors.optString(it, ""), backdrop) }
        val seededBg = pick(listOf("background", "status_bar_bg"), 0xFF000000L)
        val fgSeed = pick(listOf("ui_text", "banner_text", "status_bar_text"), seededBg ?: 0xFF000000L)
        val background = seededBg ?: if (fgSeed != null && ThemeColor.luminance(fgSeed) > 0.5) 0xFF141414L else 0xFFF7F7F8L
        val dark = ThemeColor.luminance(background) < 0.4
        val foreground = fgSeed ?: if (dark) 0xFFE6E6E6L else 0xFF161616L
        val accentSeed = pick(listOf("ui_accent", "banner_accent", "banner_title"), background) ?: ThemeColor.mix(foreground, background, 0.55)
        val sidebar = ThemeColor.mix(background, foreground, if (dark) 0.02 else 0.012)
        val accent = ThemeColor.ensureContrast(accentSeed, sidebar, ACCENT_MIN_CONTRAST)
        val border = pick(listOf("ui_border", "banner_border"), background) ?: ThemeColor.mix(background, foreground, if (dark) 0.16 else 0.14)
        val mutedFg = pick(listOf("banner_dim", "session_border"), background) ?: ThemeColor.mix(foreground, background, 0.45)
        val destructive = pick(listOf("ui_error"), background) ?: 0xFFE25563L
        val palette = ThemePalette(
            background = background, foreground = foreground,
            card = ThemeColor.mix(background, foreground, if (dark) 0.04 else 0.025),
            muted = ThemeColor.mix(background, foreground, if (dark) 0.06 else 0.04),
            mutedForeground = mutedFg,
            popover = ThemeColor.mix(background, foreground, if (dark) 0.08 else 0.05),
            primary = accent, primaryForeground = ThemeColor.readableInk(accent),
            secondary = ThemeColor.mix(accent, background, if (dark) 0.72 else 0.86),
            accent = ThemeColor.mix(accent, background, if (dark) 0.82 else 0.88),
            border = border, destructive = destructive, sidebarBackground = sidebar,
            userBubble = ThemeColor.mix(background, accent, if (dark) 0.18 else 0.12), userBubbleBorder = border
        )
        // A skin is one-mode: the same palette in both slots.
        return DesktopTheme(name, name.replaceFirstChar { it.uppercase() }, palette, null)
    }

    /** What the app paints: a chosen built-in, or (blank choice) the server's skin. */
    fun resolve(choice: String, serverSkin: JSONObject?): DesktopTheme =
        if (choice.isBlank()) fromServerSkin(serverSkin) else builtIn[choice] ?: nous
}
