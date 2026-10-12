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
 * Hermes family design tokens.
 *
 * The dark palette is the canonical Hermes Desktop look ("Hermes Teal",
 * LENS_0): deep teal canvas #041C1C, warm cream ink #FFE6CB and the brand
 * gold glow #FFBD38. The light palette keeps the same warmth on paper —
 * cream surfaces, teal ink and deep-gold accents — so both modes read as
 * the same product family.
 *
 * Component language follows beautifului.dev primitives: hairline strokes,
 * dashed separators, tinted chips, mono meta labels.
 */
object HermesTokens {
    // Dark (Hermes Teal) ----------------------------------------------------
    val Ink = Color(0xFF041C1C)          // page background
    val InkRaised = Color(0xFF0A2624)    // raised surface
    val InkSoft = Color(0xFF07211F)      // sunken / inset surface
    val InkHigh = Color(0xFF0E2E2B)      // highest surface

    // Light (Hermes Cream) --------------------------------------------------
    val Paper = Color(0xFFFAF6EC)
    val PaperRaised = Color(0xFFFFFFFF)
    val PaperSoft = Color(0xFFF3EEE1)

    // Brand -----------------------------------------------------------------
    val Gold = Color(0xFFFFBD38)         // brand glow gold (dark accent)
    val GoldStrong = Color(0xFFA16D0A)   // deep gold for light surfaces
    val Cream = Color(0xFFFFE6CB)        // warm cream ink (desktop midground)
    val Emerald = Color(0xFF34D399)      // desktop series-output token

    // Dark text -------------------------------------------------------------
    val TextDark = Cream
    val TextDarkMuted = Color(0xFFA0B5AC)
    val TextDarkFaint = Color(0xFF6E8780)

    // Light text ------------------------------------------------------------
    val TextLight = Color(0xFF21302C)
    val TextLightMuted = Color(0xFF5F6F68)
    val TextLightFaint = Color(0xFF8C9A92)

    // Strokes ---------------------------------------------------------------
    val StrokeDark = Color(0xFF223431)
    val StrokeDarkStrong = Color(0xFF31403B)
    val StrokeLight = Color(0xFFE8E1CF)
    val StrokeLightStrong = Color(0xFFD9D0BA)

    // Semantic ----------------------------------------------------------------
    val Danger = Color(0xFFF87C7C)
    val DangerLight = Color(0xFFD64545)
    val Success = Emerald
    val SuccessLight = Color(0xFF1E9E63)
    val Warning = Color(0xFFFFC24D)
    val WarningLight = Color(0xFFB97F0E)

    // Derived tints (composite helpers for chip backgrounds) -----------------
    fun goldTint(on: Color, alpha: Float = 0.14f): Color = Gold.copy(alpha = alpha).compositeOver(on)
    fun creamTint(on: Color, alpha: Float = 0.07f): Color = Cream.copy(alpha = alpha).compositeOver(on)
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
    glow = HermesTokens.Gold.copy(alpha = 0.35f),
    accentTint = HermesTokens.Gold.copy(alpha = 0.13f).compositeOver(HermesTokens.InkRaised),
    greenTint = HermesTokens.Emerald.copy(alpha = 0.14f).compositeOver(HermesTokens.InkRaised),
    orangeTint = Color(0xFFF59E42).copy(alpha = 0.14f).compositeOver(HermesTokens.InkRaised),
    redTint = HermesTokens.Danger.copy(alpha = 0.13f).compositeOver(HermesTokens.InkRaised),
    field = Color(0xFF0C2624),
    hover = Color(0xFF10302C),
    userBubble = Color(0xFF133029),
    userBubbleBorder = HermesTokens.Gold.copy(alpha = 0.22f),
    codeBlock = Color(0xFF021413),
    codeBlockBorder = HermesTokens.StrokeDark,
    isDark = true
)

private val LightHermesPalette = HermesPalette(
    line = HermesTokens.StrokeLight,
    lineStrong = HermesTokens.StrokeLightStrong,
    glow = HermesTokens.Gold.copy(alpha = 0.30f),
    accentTint = Color(0xFFF6EBD2),
    greenTint = Color(0xFFE2F3E9),
    orangeTint = Color(0xFFFCEEDD),
    redTint = Color(0xFFF9E7E7),
    field = Color(0xFFF5F0E3),
    hover = Color(0xFFF1EBDD),
    userBubble = Color(0xFFF6EED9),
    userBubbleBorder = Color(0xFFE3D9C0),
    codeBlock = Color(0xFFF3EEE1),
    codeBlockBorder = HermesTokens.StrokeLight,
    isDark = false
)

/**
 * Context-window category series (Desktop's `--context-usage-*` roles): one stable hue per
 * `session.context_breakdown` category id, brighter on ink, deeper on paper.
 */
fun HermesPalette.contextSeries(id: String): Color = when (id) {
    "system_prompt" -> if (isDark) HermesTokens.Danger else HermesTokens.DangerLight
    "tool_definitions" -> if (isDark) Color(0xFFA78BFA) else Color(0xFF7354C9)
    "rules" -> if (isDark) Color(0xFFF59E42) else Color(0xFFC2650E)
    "skills" -> if (isDark) HermesTokens.Gold else HermesTokens.GoldStrong
    "mcp" -> if (isDark) Color(0xFF38D0E8) else Color(0xFF0E8AA0)
    "subagent_definitions" -> if (isDark) HermesTokens.Emerald else HermesTokens.SuccessLight
    "memory" -> if (isDark) Color(0xFFF472B6) else Color(0xFFBE3F82)
    "conversation" -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2F6BD0)
    else -> if (isDark) HermesTokens.TextDarkMuted else HermesTokens.TextLightMuted
}

/** Ring / hint color for how full the context window is. */
fun HermesPalette.contextPressure(percent: Int?): Color = when {
    percent == null -> if (isDark) HermesTokens.TextDarkFaint else HermesTokens.TextLightFaint
    percent >= 85 -> if (isDark) HermesTokens.Danger else HermesTokens.DangerLight
    percent >= 60 -> if (isDark) HermesTokens.Warning else HermesTokens.WarningLight
    else -> if (isDark) HermesTokens.Emerald else HermesTokens.SuccessLight
}

val LocalHermesPalette = staticCompositionLocalOf { DarkHermesPalette }

/** Convenience accessor: MaterialTheme.hermes.line, .field, .accentTint, ... */
val MaterialTheme.hermes: HermesPalette
    @Composable get() = LocalHermesPalette.current

private val DarkColors = darkColorScheme(
    primary = HermesTokens.Gold,
    onPrimary = Color(0xFF241A02),
    primaryContainer = Color(0xFF3A2F0E),
    onPrimaryContainer = HermesTokens.Cream,
    background = HermesTokens.Ink,
    onBackground = HermesTokens.TextDark,
    surface = HermesTokens.Ink,
    onSurface = HermesTokens.TextDark,
    surfaceVariant = HermesTokens.InkRaised,
    onSurfaceVariant = HermesTokens.TextDarkMuted,
    outline = HermesTokens.StrokeDarkStrong,
    secondary = HermesTokens.Cream,
    onSecondary = Color(0xFF241A02),
    secondaryContainer = HermesTokens.InkRaised,
    onSecondaryContainer = HermesTokens.TextDark,
    tertiary = HermesTokens.TextDarkMuted,
    surfaceContainerLowest = HermesTokens.InkSoft,
    surfaceContainerLow = Color(0xFF07211F),
    surfaceContainer = Color(0xFF0A2624),
    surfaceContainerHigh = Color(0xFF0E2E2B),
    surfaceContainerHighest = Color(0xFF143734),
    surfaceTint = Color.Transparent,
    outlineVariant = HermesTokens.StrokeDark,
    error = HermesTokens.Danger
)

private val LightColors = lightColorScheme(
    primary = HermesTokens.GoldStrong,
    onPrimary = Color(0xFFFFF8E8),
    primaryContainer = Color(0xFFF2E3BC),
    onPrimaryContainer = Color(0xFF3A2E0C),
    background = HermesTokens.Paper,
    onBackground = HermesTokens.TextLight,
    surface = HermesTokens.Paper,
    onSurface = HermesTokens.TextLight,
    surfaceVariant = HermesTokens.PaperSoft,
    onSurfaceVariant = HermesTokens.TextLightMuted,
    outline = HermesTokens.StrokeLightStrong,
    secondary = Color(0xFF2E4B45),
    onSecondary = Color(0xFFF6F1E2),
    secondaryContainer = Color(0xFFE7EFE9),
    onSecondaryContainer = HermesTokens.TextLight,
    tertiary = HermesTokens.TextLightMuted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2E5),
    surfaceContainer = Color(0xFFF3EEE1),
    surfaceContainerHigh = Color(0xFFEEE8D8),
    surfaceContainerHighest = Color(0xFFE8E1CF),
    surfaceTint = Color.Transparent,
    outlineVariant = HermesTokens.StrokeLight,
    error = HermesTokens.DangerLight
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
