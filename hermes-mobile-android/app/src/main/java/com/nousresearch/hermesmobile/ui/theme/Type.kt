package com.nousresearch.hermesmobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nousresearch.hermesmobile.R

/**
 * Hermes family typefaces, bundled as variable fonts (OFL licensed) so the
 * app renders identically offline:
 *  - Inter — the sans used across the Hermes surfaces (desktop dashboards ship Inter).
 *  - JetBrains Mono — the desktop terminal / code face.
 * Compose maps each registered weight/style onto the variable `wght` axis
 * automatically (FontVariation.Settings defaults), minSdk 26 supports it.
 */
val HermesSans = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.inter_variable, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.inter_variable, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.inter_variable, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.inter_variable_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_variable_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.inter_variable_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.inter_variable_italic, FontWeight.Bold, FontStyle.Italic)
)

val HermesMono = FontFamily(
    Font(R.font.jetbrains_mono_variable, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.jetbrains_mono_variable, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.jetbrains_mono_variable, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.jetbrains_mono_variable_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_variable_italic, FontWeight.Bold, FontStyle.Italic)
)

/**
 * Type scale tuned for the Hermes family: desktop uses a 15px base with
 * comfortable line height and slightly tightened headings. Meta labels run
 * small and tracked out, in the beautifului primitives style.
 */
val HermesTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = HermesSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)
