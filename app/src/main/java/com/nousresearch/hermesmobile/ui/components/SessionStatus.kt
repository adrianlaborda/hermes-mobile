package com.nousresearch.hermesmobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.SessionDot
import com.nousresearch.hermesmobile.ui.theme.HermesTokens
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlin.math.cos
import kotlin.math.sin

/** Desktop's `bg-amber-500`: the one "act now" color. */
private val NeedsInput = Color(0xFFF59E0B)

/**
 * Hermes Desktop's session status dot (`session-status-dot.tsx`): 6 dp, color only, no motion — the row's arc
 * carries the motion. Amber: waiting for an answer. Accent: working. Green: finished while you were elsewhere.
 * Nothing for a settled chat (Desktop's faint idle dot only aligns titles, which the avatar already does here).
 */
@Composable
fun SessionStatusDot(dot: SessionDot, modifier: Modifier = Modifier) {
    val (color, label) = when (dot) {
        SessionDot.NEEDS_INPUT -> NeedsInput to stringResource(R.string.session_dot_needs_input)
        SessionDot.WORKING -> MaterialTheme.colorScheme.primary to stringResource(R.string.session_dot_working)
        SessionDot.UNREAD -> (if (MaterialTheme.hermes.isDark) HermesTokens.Success else HermesTokens.SuccessLight) to
            stringResource(R.string.session_dot_unread)
        SessionDot.NONE -> return
    }
    Box(modifier.size(6.dp).background(color, CircleShape).semantics { contentDescription = label })
}

/**
 * Desktop's working-row arc (`.arc-border.arc-row`): a thin ring whose bright spike travels around the row while
 * its turn runs. Same angle (160°), period (2.23 s) and stops as Desktop's repeating gradient; foreground ink in
 * dark themes, the muted midground in light ones. Respects the system's "remove animations".
 */
fun Modifier.runningArc(active: Boolean, cornerRadius: Float = 10f): Modifier = if (!active) this else composed {
    val dark = MaterialTheme.hermes.isDark
    val c1 = if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val animate = android.provider.Settings.Global.getFloat(
        LocalContext.current.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) != 0f
    val phase by rememberInfiniteTransition(label = "arc").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2230, easing = LinearEasing), RepeatMode.Restart), label = "arc"
    )
    drawWithContent {
        drawContent()
        val stroke = 1.25.dp.toPx()
        val inset = stroke / 2 + 2.dp.toPx()
        // One gradient period spans the row's diagonal; the spike moves one period per cycle along 160°.
        val period = kotlin.math.hypot(size.width, size.height) * 1.2f
        val angle = Math.toRadians(160.0 - 90.0)
        val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val shift = if (animate) phase * period else 0f
        val start = dir * shift
        val brush = Brush.linearGradient(
            0f to Color.Transparent, 0.375f to Color.Transparent, 0.5f to c1,
            0.625f to c1.copy(alpha = 0.45f * c1.alpha), 0.875f to Color.Transparent, 1f to Color.Transparent,
            start = start, end = start + dir * period, tileMode = TileMode.Repeated
        )
        drawRoundRect(
            brush, topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = CornerRadius(cornerRadius.dp.toPx()), style = Stroke(stroke)
        )
    }
}
