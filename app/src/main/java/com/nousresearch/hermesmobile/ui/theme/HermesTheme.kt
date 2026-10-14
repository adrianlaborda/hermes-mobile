package com.nousresearch.hermesmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Hermes family design tokens: Hermes Desktop's default "Nous" skin
 * (apps/desktop/src/themes, apps/shared/src/theme-presets.ts in hermes-agent).
 *
 * Nous is GitHub's Light Default / Dark Default chrome carrying Nous blue. The brand blue
 * #0053FD is the light accent; dark carries #4A84FE, the same hue lifted to clear AA on
 * near-black. Semantic colors are Desktop's `--ui-*` palette, so status, diff and context
 * series read the same on phone and desktop. Copy values from upstream; do not hand-tune.
 */
object HermesTokens {
    // Dark (Nous dark) -------------------------------------------------------
    val Ink = Color(0xFF0D1117)          // page background (Desktop background)
    val InkRaised = Color(0xFF161B22)    // raised surface: sheets, menus, fields (popover)
    val InkSoft = Color(0xFF010409)      // sunken surface: drawer, code (card / sidebar)
    val InkHigh = Color(0xFF1A1E24)      // highest surface (muted)

    // Light (Nous light) -----------------------------------------------------
    val Paper = Color(0xFFFFFFFF)
    val PaperRaised = Color(0xFFFFFFFF)
    val PaperSoft = Color(0xFFF6F8FA)

    // Brand ------------------------------------------------------------------
    val NousBlue = Color(0xFF0053FD)     // brand accent on light surfaces
    val NousBlueDark = Color(0xFF4A84FE) // same hue for dark surfaces
    val NousCream = Color(0xFFFFE6CB)    // Nous cream, kept for brand moments only

    // Dark text --------------------------------------------------------------
    val TextDark = Color(0xFFE6EDF3)
    val TextDarkMuted = Color(0xFF7D8590)
    val TextDarkFaint = Color(0xFF6E7681)

    // Light text -------------------------------------------------------------
    val TextLight = Color(0xFF1F2328)
    val TextLightMuted = Color(0xFF656D76)
    val TextLightFaint = Color(0xFF6E7781)

    // Strokes ----------------------------------------------------------------
    val StrokeDark = Color(0xFF21262D)
    val StrokeDarkStrong = Color(0xFF30363D)
    val StrokeLight = Color(0xFFD8DEE4)
    val StrokeLightStrong = Color(0xFFD0D7DE)

    // Semantic (Desktop --ui-*) ------------------------------------------------
    val Danger = Color(0xFFE75E78)
    val DangerLight = Color(0xFFCF2D56)
    val Success = Color(0xFF55A583)
    val SuccessLight = Color(0xFF1F8A65)
    val Warning = Color(0xFFC08532)
    val WarningLight = Color(0xFFC08532)
    val Orange = Color(0xFFDB704B)
    val Purple = Color(0xFF9E94D5)
    val Cyan = Color(0xFF6F9BA6)
    val CyanLight = Color(0xFF4C7F8C)
}

/**
 * Theme-resolved component tokens — the beautifului-style structure layer
 * (lines, tints, fields, hovers) that adapts to light/dark.
 */
data class HermesPalette(
    val line: Color,
    val lineStrong: Color,
    val glow: Color,
    val accentTint: Color,
    val greenTint: Color,
    val orangeTint: Color,
    val redTint: Color,
    val field: Color,
    val hover: Color,
    val userBubble: Color,
    val userBubbleBorder: Color,
    val codeBlock: Color,
    val codeBlockBorder: Color,
    val isDark: Boolean
)

private val DarkHermesPalette = HermesPalette(
    line = HermesTokens.StrokeDark,
    lineStrong = HermesTokens.StrokeDarkStrong,
    glow = HermesTokens.NousBlueDark.copy(alpha = 0.35f),
    accentTint = Color(0xFF17243A),                                  // Nous dark accent
    greenTint = HermesTokens.Success.copy(alpha = 0.12f).compositeOver(HermesTokens.Ink),
    orangeTint = HermesTokens.Orange.copy(alpha = 0.12f).compositeOver(HermesTokens.Ink),
    redTint = HermesTokens.Danger.copy(alpha = 0.12f).compositeOver(HermesTokens.Ink),
    field = HermesTokens.InkRaised,
    hover = Color(0xFF1D2E4F).copy(alpha = 0.45f).compositeOver(HermesTokens.Ink), // Nous dark secondary
    userBubble = Color(0xFF07162C),
    userBubbleBorder = HermesTokens.StrokeDarkStrong,
    codeBlock = HermesTokens.InkSoft,
    codeBlockBorder = HermesTokens.StrokeDarkStrong,
    isDark = true
)

private val LightHermesPalette = HermesPalette(
    line = HermesTokens.StrokeLight,
    lineStrong = HermesTokens.StrokeLightStrong,
    glow = HermesTokens.NousBlue.copy(alpha = 0.25f),
    accentTint = Color(0xFFE3EDFF),                                  // Nous light accent
    greenTint = HermesTokens.SuccessLight.copy(alpha = 0.12f).compositeOver(HermesTokens.Paper),
    orangeTint = HermesTokens.Orange.copy(alpha = 0.12f).compositeOver(HermesTokens.Paper),
    redTint = HermesTokens.DangerLight.copy(alpha = 0.12f).compositeOver(HermesTokens.Paper),
    field = HermesTokens.PaperSoft,
    hover = Color(0xFFDEEAFF).copy(alpha = 0.6f).compositeOver(HermesTokens.Paper), // Nous light secondary
    userBubble = Color(0xFFDAE7FD),
    userBubbleBorder = HermesTokens.StrokeLightStrong,
    codeBlock = HermesTokens.PaperSoft,
    codeBlockBorder = HermesTokens.StrokeLightStrong,
    isDark = false
)

private fun mix(a: Color, b: Color, aShare: Float) = Color(
    red = a.red * aShare + b.red * (1 - aShare),
    green = a.green * aShare + b.green * (1 - aShare),
    blue = a.blue * aShare + b.blue * (1 - aShare)
)

/**
 * Context-window category series: Desktop's `--context-usage-*` roles, one stable hue per
 * `session.context_breakdown` category id.
 */
fun HermesPalette.contextSeries(id: String): Color {
    val text = if (isDark) HermesTokens.TextDark else HermesTokens.TextLight
    val page = if (isDark) HermesTokens.Ink else HermesTokens.Paper
    val red = if (isDark) HermesTokens.Danger else HermesTokens.DangerLight
    val green = if (isDark) HermesTokens.Success else HermesTokens.SuccessLight
    val cyan = if (isDark) HermesTokens.Cyan else HermesTokens.CyanLight
    val blue = if (isDark) HermesTokens.NousBlueDark else HermesTokens.NousBlue
    return when (id) {
        "system_prompt" -> mix(text, page, 0.55f)
        "tool_definitions" -> HermesTokens.Purple
        "rules" -> green
        "skills" -> HermesTokens.Warning
        "mcp" -> mix(red, HermesTokens.Purple, 0.72f)
        "subagent_definitions" -> mix(blue, cyan, 0.70f)
        "memory" -> mix(HermesTokens.Orange, HermesTokens.Warning, 0.80f)
        "conversation" -> cyan
        else -> if (isDark) HermesTokens.TextDarkMuted else HermesTokens.TextLightMuted
    }
}

/** Ring / hint color for how full the context window is: brand blue, then amber, then red. */
fun HermesPalette.contextPressure(percent: Int?): Color = when {
    percent == null -> if (isDark) HermesTokens.TextDarkFaint else HermesTokens.TextLightFaint
    percent >= 85 -> if (isDark) HermesTokens.Danger else HermesTokens.DangerLight
    percent >= 60 -> if (isDark) HermesTokens.Warning else HermesTokens.WarningLight
    else -> if (isDark) HermesTokens.NousBlueDark else HermesTokens.NousBlue
}

val LocalHermesPalette = staticCompositionLocalOf { DarkHermesPalette }

/** Convenience accessor: MaterialTheme.hermes.line, .field, .accentTint, ... */
val MaterialTheme.hermes: HermesPalette
    @Composable get() = LocalHermesPalette.current

private val DarkColors = darkColorScheme(
    primary = HermesTokens.NousBlueDark,
    onPrimary = Color(0xFF161616),
    primaryContainer = Color(0xFF1D2E4F),
    onPrimaryContainer = HermesTokens.TextDark,
    background = HermesTokens.Ink,
    onBackground = HermesTokens.TextDark,
    surface = HermesTokens.Ink,
    onSurface = HermesTokens.TextDark,
    surfaceVariant = HermesTokens.InkRaised,
    onSurfaceVariant = HermesTokens.TextDarkMuted,
    outline = HermesTokens.StrokeDarkStrong,
    secondary = Color(0xFF1D2E4F),
    onSecondary = HermesTokens.TextDark,
    secondaryContainer = Color(0xFF17243A),
    onSecondaryContainer = HermesTokens.TextDark,
    tertiary = HermesTokens.TextDarkMuted,
    surfaceContainerLowest = HermesTokens.InkSoft,
    surfaceContainerLow = HermesTokens.InkSoft,
    surfaceContainer = HermesTokens.InkRaised,
    surfaceContainerHigh = HermesTokens.InkRaised,
    surfaceContainerHighest = HermesTokens.InkHigh,
    surfaceTint = Color.Transparent,
    outlineVariant = HermesTokens.StrokeDark,
    error = Color(0xFFF85149),
    onError = Color(0xFFFFFFFF)
)

private val LightColors = lightColorScheme(
    primary = HermesTokens.NousBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEEAFF),
    onPrimaryContainer = HermesTokens.TextLight,
    background = HermesTokens.Paper,
    onBackground = HermesTokens.TextLight,
    surface = HermesTokens.Paper,
    onSurface = HermesTokens.TextLight,
    surfaceVariant = HermesTokens.PaperSoft,
    onSurfaceVariant = HermesTokens.TextLightMuted,
    outline = HermesTokens.StrokeLightStrong,
    secondary = Color(0xFFDEEAFF),
    onSecondary = HermesTokens.TextLight,
    secondaryContainer = Color(0xFFE3EDFF),
    onSecondaryContainer = HermesTokens.TextLight,
    tertiary = HermesTokens.TextLightMuted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = HermesTokens.PaperSoft,
    surfaceContainer = HermesTokens.PaperSoft,
    surfaceContainerHigh = Color(0xFFF0F2F4),
    surfaceContainerHighest = Color(0xFFEAEEF2),
    surfaceTint = Color.Transparent,
    outlineVariant = HermesTokens.StrokeLight,
    error = Color(0xFFCF222E),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun HermesTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val choice = com.nousresearch.hermesmobile.ui.rememberAppearance()
    val dark = darkTheme ?: when(choice) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val palette = if (dark) DarkHermesPalette else LightHermesPalette
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = HermesTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = {
            androidx.compose.runtime.CompositionLocalProvider(LocalHermesPalette provides palette) {
                content()
            }
        }
    )
}
