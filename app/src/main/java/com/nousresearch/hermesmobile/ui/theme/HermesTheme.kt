package com.nousresearch.hermesmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Hermes family design tokens. The app paints Hermes Desktop's themes ([DesktopThemes]): by default the
 * server's Hermes skin, which Desktop resolves the same way, so both apps wear the same theme. The stock
 * skin is Desktop's default *Nous*: GitHub chrome carrying Nous blue.
 *
 * What does not change with the theme lives here: Desktop's semantic `--ui-*` colors (status, diffs,
 * context series), which are global in Desktop too, plus the Nous brand constants.
 */
object HermesTokens {
    // Brand ------------------------------------------------------------------
    val NousBlue = Color(0xFF0053FD)     // brand accent on light surfaces
    val NousBlueDark = Color(0xFF4A84FE) // same hue for dark surfaces

    // Semantic (Desktop --ui-*, dark overrides where Desktop has them) ------------
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
 * (lines, tints, fields, hovers) that adapts to the active theme.
 */
data class HermesPalette(
    val background: Color,
    val foreground: Color,
    val primary: Color,
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

private fun c(argb: Long) = Color(argb)

private fun hermesPalette(t: ThemePalette): HermesPalette {
    val dark = t.isDark
    val bg = c(t.background)
    return HermesPalette(
        background = bg,
        foreground = c(t.foreground),
        primary = c(t.primary),
        line = c(ThemeColor.mix(t.border, t.background, 0.35)),
        lineStrong = c(t.border),
        glow = c(t.primary).copy(alpha = 0.3f),
        accentTint = c(t.accent),
        greenTint = (if (dark) HermesTokens.Success else HermesTokens.SuccessLight).copy(alpha = 0.12f).compositeOver(bg),
        orangeTint = HermesTokens.Orange.copy(alpha = 0.12f).compositeOver(bg),
        redTint = (if (dark) HermesTokens.Danger else HermesTokens.DangerLight).copy(alpha = 0.12f).compositeOver(bg),
        field = c(t.muted),
        hover = c(ThemeColor.mix(t.background, t.secondary, 0.5)),
        userBubble = c(t.userBubble),
        userBubbleBorder = c(t.userBubbleBorder),
        codeBlock = c(t.card),
        codeBlockBorder = c(t.border),
        isDark = dark
    )
}

private fun colorScheme(t: ThemePalette) = if (t.isDark) darkColorScheme(
    primary = c(t.primary), onPrimary = c(t.primaryForeground),
    primaryContainer = c(t.secondary), onPrimaryContainer = c(t.foreground),
    background = c(t.background), onBackground = c(t.foreground),
    surface = c(t.background), onSurface = c(t.foreground),
    surfaceVariant = c(t.popover), onSurfaceVariant = c(t.mutedForeground),
    outline = c(t.border), outlineVariant = c(ThemeColor.mix(t.border, t.background, 0.35)),
    secondary = c(t.secondary), onSecondary = c(t.foreground),
    secondaryContainer = c(t.accent), onSecondaryContainer = c(t.foreground),
    tertiary = c(t.mutedForeground),
    surfaceContainerLowest = c(t.sidebarBackground), surfaceContainerLow = c(t.sidebarBackground),
    surfaceContainer = c(t.popover), surfaceContainerHigh = c(t.popover), surfaceContainerHighest = c(t.muted),
    surfaceTint = Color.Transparent,
    error = c(t.destructive), onError = c(ThemeColor.readableInk(t.destructive))
) else lightColorScheme(
    primary = c(t.primary), onPrimary = c(t.primaryForeground),
    primaryContainer = c(t.secondary), onPrimaryContainer = c(t.foreground),
    background = c(t.background), onBackground = c(t.foreground),
    surface = c(t.background), onSurface = c(t.foreground),
    surfaceVariant = c(t.card), onSurfaceVariant = c(t.mutedForeground),
    outline = c(t.border), outlineVariant = c(ThemeColor.mix(t.border, t.background, 0.35)),
    secondary = c(t.secondary), onSecondary = c(t.foreground),
    secondaryContainer = c(t.accent), onSecondaryContainer = c(t.foreground),
    tertiary = c(t.mutedForeground),
    surfaceContainerLowest = c(t.popover), surfaceContainerLow = c(t.sidebarBackground),
    surfaceContainer = c(t.card), surfaceContainerHigh = c(t.muted), surfaceContainerHighest = c(t.muted),
    surfaceTint = Color.Transparent,
    error = c(t.destructive), onError = c(ThemeColor.readableInk(t.destructive))
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
    val red = if (isDark) HermesTokens.Danger else HermesTokens.DangerLight
    val green = if (isDark) HermesTokens.Success else HermesTokens.SuccessLight
    val cyan = if (isDark) HermesTokens.Cyan else HermesTokens.CyanLight
    val blue = if (isDark) HermesTokens.NousBlueDark else HermesTokens.NousBlue
    return when (id) {
        "system_prompt" -> mix(foreground, background, 0.55f)
        "tool_definitions" -> HermesTokens.Purple
        "rules" -> green
        "skills" -> HermesTokens.Warning
        "mcp" -> mix(red, HermesTokens.Purple, 0.72f)
        "subagent_definitions" -> mix(blue, cyan, 0.70f)
        "memory" -> mix(HermesTokens.Orange, HermesTokens.Warning, 0.80f)
        "conversation" -> cyan
        else -> mix(foreground, background, 0.55f)
    }
}

/** Ring / hint color for how full the context window is: the theme accent, then amber, then red. */
fun HermesPalette.contextPressure(percent: Int?): Color = when {
    percent == null -> mix(foreground, background, 0.45f)
    percent >= 85 -> if (isDark) HermesTokens.Danger else HermesTokens.DangerLight
    percent >= 60 -> if (isDark) HermesTokens.Warning else HermesTokens.WarningLight
    else -> primary
}

val LocalHermesPalette = staticCompositionLocalOf { hermesPalette(DesktopThemes.nous.paletteFor(true)) }

/** Convenience accessor: MaterialTheme.hermes.line, .field, .accentTint, ... */
val MaterialTheme.hermes: HermesPalette
    @Composable get() = LocalHermesPalette.current

@Composable
fun HermesTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val choice = com.nousresearch.hermesmobile.ui.rememberAppearance()
    val darkMode = darkTheme ?: when(choice) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val theme = com.nousresearch.hermesmobile.ui.rememberDesktopTheme()
    val palette = theme.paletteFor(darkMode)
    // A single-palette theme paints its own polarity whatever the mode, as Desktop does.
    val dark = palette.isDark
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val hermesPalette = remember(palette) { hermesPalette(palette) }
    val scheme = remember(palette) { colorScheme(palette) }
    MaterialTheme(
        colorScheme = scheme,
        typography = HermesTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = {
            androidx.compose.runtime.CompositionLocalProvider(LocalHermesPalette provides hermesPalette) {
                content()
            }
        }
    )
}
