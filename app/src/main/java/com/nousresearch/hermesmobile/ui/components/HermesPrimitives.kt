package com.nousresearch.hermesmobile.ui.components

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import android.graphics.BitmapFactory
import android.util.Base64

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nousresearch.hermesmobile.data.ChatMessage
import com.nousresearch.hermesmobile.data.HermesProfile
import com.nousresearch.hermesmobile.data.ServerInteraction
import com.nousresearch.hermesmobile.data.ToolActivity
import com.nousresearch.hermesmobile.ui.theme.HermesTokens
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Circular progress ring — the context-usage meter from the Hermes reference UI. */
@Composable
fun ContextRing(
    percent: Int?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.hermes.line,
    strokeWidth: Dp = 2.5.dp
) {
    val stroke = strokeWidth
    // Live usage moves the ring every second during a turn; ease between readings instead of jumping.
    val fraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = (percent ?: 0).coerceIn(0, 100) / 100f,
        animationSpec = androidx.compose.animation.core.tween(450),
        label = "context-ring"
    )
    val arcColor by androidx.compose.animation.animateColorAsState(color, label = "context-ring-color")
    androidx.compose.foundation.Canvas(modifier) {
        val s = stroke.toPx()
        // Track and arc share one square inset by half the stroke, so the arc sits exactly on the track.
        val diameter = size.minDimension - s
        drawCircle(color = track, radius = diameter / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(s))
        if (fraction > 0f) {
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = androidx.compose.ui.graphics.drawscope.Stroke(s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

/** Hairline dashed separator — a signature detail of the Hermes component language. */
@Composable
fun DashedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.hermes.line,
    thickness: Dp = 1.dp,
    dash: Dp = 6.dp,
    gap: Dp = 5.dp,
    horizontalPadding: Dp = 0.dp
) {
    val dashPx = dash.value
    val gapPx = gap.value
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(thickness)
            .drawBehind {
                val y = size.height / 2
                drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx * density, gapPx * density))
                )
            }
    )
}

/** Soft pulsing dots — the "thinking" state from the beautifului primitives. */
@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier,
    label: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 7.dp,
    spacing: Dp = 4.dp
) {
    val transition = rememberInfiniteTransition(label = "thinking")
    @Composable
    fun dot(phase: Int): Float = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, delayMillis = phase * 160, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot$phase"
    ).value
    Row(
        modifier.heightIn(min = dotSize * 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing + 4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            repeat(3) { i ->
                val a = dot(i)
                Box(
                    Modifier
                        .size(dotSize)
                        .alpha(a)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
        if (!label.isNullOrBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Small tinted status chip — dot + text, for connection and task states. */
@Composable
fun StatusPill(
    text: String,
    good: Boolean = false,
    warning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tint = when {
        good -> MaterialTheme.hermes.greenTint
        warning -> MaterialTheme.hermes.orangeTint
        else -> MaterialTheme.hermes.accentTint
    }
    val fg = when {
        good -> HermesTokens.Success.takeIf { MaterialTheme.hermes.isDark } ?: HermesTokens.SuccessLight
        warning -> HermesTokens.Warning.takeIf { MaterialTheme.hermes.isDark } ?: HermesTokens.WarningLight
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier,
        color = tint,
        contentColor = fg,
        shape = CircleShape,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.22f))
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun HermesBrandMark(modifier: Modifier = Modifier, size: Int = 36) {
    Image(androidx.compose.ui.res.painterResource(R.drawable.hermes_desktop),
        contentDescription = "Hermes", modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * .22f).dp))
            .border(1.dp, MaterialTheme.hermes.line, RoundedCornerShape((size * .22f).dp)))
}

@Composable
fun HermesTopBar(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    /** Replaces [subtitle] with something interactive, such as [ProfileSwitcher]. */
    subtitleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitleContent != null) subtitleContent()
            else if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

/**
 * The bar of a view inside a page (a folder, a project): smaller than [HermesTopBar], with a close button while
 * something is open in it. The page's own top bar, with the menu, stays above.
 */
@Composable
fun SubBar(title: String, onClose: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = if (onClose == null) 18.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.ui_19766ed6cc)) }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/**
 * A page's profile as its top bar subtitle: tapping it lists the others, as the chat header does for a new chat.
 * Plain text when there is nothing to switch to.
 */
@Composable
fun ProfileSwitcher(profile: String, profiles: List<String>, onProfile: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val switchable = profiles.size > 1
    Box {
        Row(
            (if (switchable) Modifier.clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(R.string.chat_change_profile)) { open = true } else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.i18n_profile_named, profile), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (switchable) Icon(Icons.Default.UnfoldMore, null, Modifier.padding(start = 2.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            profiles.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { if (name == profile) Icon(Icons.Default.Check, null) },
                    onClick = { open = false; if (name != profile) onProfile(name) }
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, meta: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        DashedDivider(Modifier.weight(1f), horizontalPadding = 0.dp)
        meta?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FlatRow(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(10.dp))
            it()
        }
    }
}

@Composable
fun ProfileAvatar(
    profile: HermesProfile,
    modifier: Modifier = Modifier,
    size: Int = 42,
    avatarDataUrl: String? = null
) {
    val initials = profile.label.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
        .ifBlank { "H" }
    val botColor = parseHermesBotColor(profile.botMeta?.optString("color").orEmpty())
    val background = botColor ?: MaterialTheme.hermes.accentTint
    val shapeName = profile.botMeta?.optString("shape").orEmpty().lowercase()
    val avatarShape = if (shapeName == "circle") CircleShape else RoundedCornerShape((size * .28f).dp)
    val bitmap by produceState<android.graphics.Bitmap?>(null, avatarDataUrl, size) {
        value = if (avatarDataUrl.isNullOrBlank()) null else withContext(Dispatchers.Default) {
            decodeAvatarDataUrl(avatarDataUrl, size * 3)
        }
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(avatarShape)
            .background(background)
            .border(1.dp, MaterialTheme.hermes.line, avatarShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap).asImageBitmap(),
                contentDescription = "${profile.label} avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                initials,
                fontWeight = FontWeight.SemiBold,
                color = if (botColor != null) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun decodeAvatarDataUrl(dataUrl: String?, targetPx: Int): android.graphics.Bitmap? {
    if (dataUrl.isNullOrBlank()) return null
    return runCatching {
        val encoded = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
        if (encoded.isBlank()) return@runCatching null
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > targetPx * 2 || bounds.outHeight / sample > targetPx * 2) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

private fun parseHermesBotColor(raw: String): Color? {
    val hex = raw.trim().removePrefix("#")
    if (hex.length !in setOf(6, 8)) return null
    val value = hex.toULongOrNull(16) ?: return null
    val argb = if (hex.length == 6) 0xFF000000uL or value else value
    return Color(argb.toLong())
}

@Composable
fun StateDot(good: Boolean, warning: Boolean = false) {
    val color = when {
        good -> HermesTokens.Success.takeIf { MaterialTheme.hermes.isDark } ?: HermesTokens.SuccessLight
        warning -> HermesTokens.Warning.takeIf { MaterialTheme.hermes.isDark } ?: HermesTokens.WarningLight
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
    }
    val glow = color.copy(alpha = 0.22f)
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(glow))
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun HermesTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.hermes.line,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.hermes.accentTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun HermesLoader(label: String = stringResource(R.string.a22_connecting)) {
    Column(
        Modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ThinkingIndicator(label = label)
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onEdit: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null
) {
    if (!message.renderable) return

    val timelineKind = message.displayKind in setOf(
        "model_switch",
        "personality_switch",
        "process_complete",
        "auto_continue",
        "async_delegation_complete",
        "failed_turn",
        "steer"
    )
    if (timelineKind) {
        val label = when (message.displayKind) {
            "model_switch" -> stringResource(R.string.a22_tl_model_changed)
            "personality_switch" -> stringResource(R.string.a22_tl_personality_changed)
            "process_complete" -> stringResource(R.string.a22_tl_process_complete)
            "auto_continue" -> stringResource(R.string.a22_tl_auto_continue)
            "async_delegation_complete" -> stringResource(R.string.a22_tl_delegation_complete)
            "failed_turn" -> stringResource(R.string.a22_tl_turn_failed)
            "steer" -> stringResource(R.string.a22_tl_correction)
            else -> message.displayKind
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashedDivider(Modifier.weight(1f))
            Text(
                if (message.content.isBlank()) label else "$label · ${message.content}",
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (message.displayKind == "failed_turn") MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            DashedDivider(Modifier.weight(1f))
        }
        return
    }

    if (message.role == "tool") {
        ChatActivityBlock(listOf(message))
        return
    }
    if (message.role == "assistant" && message.reasoning.isNotBlank()) {
        Column {
            ChatActivityBlock(listOf(message.copy(content = "")))
            if (message.content.isNotBlank() || message.attachments.isNotEmpty())
                ChatMessageBubble(message.copy(reasoning = ""), onEdit, onCopy)
        }
        return
    }
    val user = message.role == "user"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        if (user) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 320.dp)) {
                Surface(
                    color = MaterialTheme.hermes.userBubble,
                    shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.hermes.userBubbleBorder)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (message.attachments.isNotEmpty()) {
                            message.attachments.take(6).forEach { name ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachFile, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (message.content.isNotBlank()) Text(message.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if ((onEdit != null || onCopy != null) && message.content.isNotBlank()) {
                    Row(
                        Modifier.padding(end = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        onEdit?.let {
                            IconButton(onClick = it, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Edit, stringResource(R.string.a22_edit_message), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f))
                            }
                        }
                        onCopy?.let {
                            IconButton(onClick = it, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.ContentCopy, stringResource(R.string.a22_copy), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f))
                            }
                        }
                    }
                }
            }
        } else if (message.role == "assistant") {
            if (message.content.isNotBlank()) MarkdownLite(com.nousresearch.hermesmobile.data.ChatMedia.render(message.content))
        }
    }
}

@Composable
fun ToolActivityLane(tools: List<ToolActivity>) {
    CompactToolActivity(tools)
}

@Composable
fun InteractionDialog(
    interaction: ServerInteraction,
    onApproval: (String) -> Unit,
    onAnswer: (String) -> Unit,
    onBatchAnswers: (Map<String, String>) -> Unit,
    onCancel: () -> Unit
) {
    var value by remember(interaction.requestId) { mutableStateOf("") }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val sensitive = interaction.method in setOf("sudo", "secret", "vault.code", "vault.unlock_prompt", "vault.save_login")
    DisposableEffect(view, sensitive) {
        var context = view.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) context = context.baseContext
        val window = (context as? android.app.Activity)?.window
        val secure = android.view.WindowManager.LayoutParams.FLAG_SECURE
        val alreadySecure = window?.attributes?.flags?.and(secure) != 0
        if (sensitive) window?.addFlags(secure)
        onDispose { if (sensitive && !alreadySecure) window?.clearFlags(secure) }
    }
    val p = interaction.params

    when (interaction.method) {
        "approval" -> {
            val choices = remember(interaction.requestId) {
                val arr = p.optJSONArray("choices")
                if (arr == null || arr.length() == 0) listOf("once", "deny")
                else buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }.filter {
                    it in setOf("once", "session", "always", "deny") &&
                        (it != "always" || p.optBoolean("allow_permanent", true)) &&
                        (it != "session" || p.optBoolean("allow_session", true))
                }.let { (it + "deny").distinct() }
            }
            AlertDialog(
                onDismissRequest = {},
                icon = {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.hermes.accentTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                },
                title = { Text(stringResource(R.string.ui_b3d861973a)) },
                text = {
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.optString("description").takeIf { it.isNotBlank() }?.let { Text(it) }
                        p.optString("command").takeIf { it.isNotBlank() }?.let {
                            Surface(
                                color = MaterialTheme.hermes.codeBlock,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.hermes.codeBlockBorder)
                            ) {
                                Text(
                                    it,
                                    Modifier.padding(12.dp),
                                    fontFamily = com.nousresearch.hermesmobile.ui.theme.HermesMono,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    // Full-width stacked choices: the primary "allow once" is the filled one,
                    // deny is always last and tinted, so the safe/unsafe split reads at a glance.
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        choices.forEach { choice ->
                            val label = when (choice) {
                                "once" -> stringResource(R.string.a22_allow_once)
                                "session" -> stringResource(R.string.a22_allow_session)
                                "always" -> stringResource(R.string.a22_allow_always)
                                "deny" -> stringResource(R.string.a22_deny)
                                else -> choice
                            }
                            val click = {
                                haptics.performHapticFeedback(
                                    if (choice == "deny") androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject
                                    else androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm
                                )
                                onApproval(choice)
                            }
                            when (choice) {
                                "once" -> Button(onClick = click, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
                                "deny" -> OutlinedButton(
                                    onClick = click,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .5f))
                                ) { Text(label) }
                                else -> OutlinedButton(onClick = click, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
                            }
                        }
                    }
                }
            )
        }

        "clarify" -> {
            val questions = p.optJSONArray("questions")
            if (questions != null && questions.length() > 0) {
                val rows = remember(interaction.requestId) {
                    (0 until questions.length()).mapNotNull { questions.optJSONObject(it) }
                        .filter { it.optString("qid").isNotBlank() }
                }
                val answers = remember(interaction.requestId) { mutableStateMapOf<String, String>() }
                val selected = remember(interaction.requestId) { mutableStateMapOf<String, List<String>>() }
                val locked = p.optJSONObject("answers") ?: org.json.JSONObject()
                fun result(q: org.json.JSONObject): String {
                    val id = q.optString("qid")
                    if (locked.has(id)) return locked.optString(id, "")
                    val choices = selected[id].orEmpty()
                    val custom = answers[id].orEmpty().trim()
                    return com.nousresearch.hermesmobile.data.ClarifyAnswer.encode(q.optBoolean("multi_select"), choices, custom)
                }
                AlertDialog(
                    onDismissRequest = onCancel,
                    title = { Text(stringResource(R.string.ui_58aecb2489)) },
                    text = {
                        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            rows.forEach { q ->
                                val id = q.optString("qid")
                                Text(q.optString("question"))
                                if (locked.has(id)) Text(if (locked.isNull(id)) stringResource(R.string.a22_skipped) else locked.optString(id))
                                else {
                                    val options = q.optJSONArray("choices")
                                    if (options != null) for (i in 0 until options.length()) {
                                        val choice = options.optString(i)
                                        if (choice.isNotBlank()) FilterChip(
                                            selected = choice in selected[id].orEmpty(),
                                            onClick = {
                                                val old = selected[id].orEmpty()
                                                selected[id] = if (q.optBoolean("multi_select")) {
                                                    if (choice in old) old - choice else old + choice
                                                } else if (choice in old) emptyList() else listOf(choice)
                                                if (!q.optBoolean("multi_select")) answers[id] = ""
                                            }, label = { Text(choice) })
                                    }
                                    HermesTextField(value = answers[id].orEmpty(), onValueChange = {
                                        answers[id] = it
                                        if (!q.optBoolean("multi_select")) selected[id] = emptyList()
                                    }, label = stringResource(R.string.a22_free_answer), singleLine = false)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(
                        onClick = { onBatchAnswers(rows.filterNot { locked.has(it.optString("qid")) && locked.isNull(it.optString("qid")) }.associate { it.optString("qid") to result(it) }) },
                        enabled = rows.all { locked.has(it.optString("qid")) || result(it).isNotBlank() }
                    ) { Text(stringResource(R.string.ui_e1d12d1a29)) } },
                    dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.ui_bb9dbb406d)) } }
                )
            } else {
                val choices = remember(interaction.requestId) {
                    val arr = p.optJSONArray("choices")
                    buildList { if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i)) }
                        .filter { it.isNotBlank() }
                }
                AlertDialog(
                    onDismissRequest = onCancel,
                    title = { Text(stringResource(R.string.ui_58aecb2489)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(p.optString("question").ifBlank { stringResource(R.string.a22_needs_info) })
                            if (choices.isNotEmpty()) {
                                choices.forEach { choice ->
                                    OutlinedButton(onClick = { onAnswer(choice) }, modifier = Modifier.fillMaxWidth()) {
                                        Text(choice, modifier = Modifier.weight(1f))
                                    }
                                }
                                Text(stringResource(R.string.ui_e3b4c25e14), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HermesTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = stringResource(R.string.a22_answer),
                                imeAction = ImeAction.Done,
                                onDone = { if (value.isNotBlank()) onAnswer(value) }
                            )
                        }
                    },
                    confirmButton = { TextButton(onClick = { onAnswer(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.ui_e1d12d1a29)) } },
                    dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.ui_bb9dbb406d)) } }
                )
            }
        }

        "vault.save_login" -> {
            var identifier by remember(interaction.requestId) { mutableStateOf("") }
            AlertDialog(properties = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn), onDismissRequest = onCancel, title = { Text(stringResource(R.string.ui_7f4d54cbca)) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(p.optString("site"))
                    Text(p.optString("origin"), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.ui_92a656b378))
                    HermesTextField(identifier, { identifier = it }, stringResource(R.string.a22_user_or_email))
                    HermesTextField(value, { value = it }, stringResource(R.string.a22_password), password = true)
                }
            }, confirmButton = { TextButton(enabled = identifier.isNotBlank() && value.isNotBlank(), onClick = {
                onAnswer(org.json.JSONObject().put("identifier", identifier).put("password", value).toString())
            }) { Text(stringResource(R.string.ui_13e51a210f)) } }, dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.ui_bb9dbb406d)) } })
        }

        else -> AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(securePolicy = if (sensitive) androidx.compose.ui.window.SecureFlagPolicy.SecureOn else androidx.compose.ui.window.SecureFlagPolicy.Inherit),
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.ui_8e19efa1d9)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(p.optString("question").ifBlank { p.optString("description").ifBlank { p.optString("prompt").ifBlank { p.optString("display_name").ifBlank { p.optString("hint").ifBlank { interaction.method } } } } })
                    HermesTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = stringResource(R.string.a22_value),
                        password = interaction.method in setOf("sudo", "secret", "vault.code", "vault.unlock_prompt"),
                        imeAction = ImeAction.Done,
                        onDone = { if (value.isNotBlank()) onAnswer(value) }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onAnswer(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.ui_e1d12d1a29)) } },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.ui_bb9dbb406d)) } }
        )
    }
}

/** Flat free-form row for richer list content. Mirrors Desktop's ListRow principle without nesting cards. */
@Composable
fun FlatRow(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(when {
                onLongClick != null -> Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                onClick != null -> Modifier.clickable(onClick = onClick)
                else -> Modifier
            })
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), content = content)
        trailing?.let { Spacer(Modifier.width(10.dp)); it() }
    }
}

/** Pill search field shared by every list (chats, bots, sheets, logs) so search looks the same everywhere. */
@Composable
fun HermesSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            when {
                loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                value.isNotEmpty() -> IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.a22_clear_search))
                }
            }
        }
    )
}
