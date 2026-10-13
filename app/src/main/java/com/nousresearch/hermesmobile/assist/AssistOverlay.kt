package com.nousresearch.hermesmobile.assist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.assist.AssistController.Phase
import com.nousresearch.hermesmobile.ui.components.HermesBrandMark
import com.nousresearch.hermesmobile.ui.components.MarkdownLite
import com.nousresearch.hermesmobile.ui.components.StatusPill
import com.nousresearch.hermesmobile.ui.components.ThinkingIndicator
import com.nousresearch.hermesmobile.ui.theme.hermes

/** The assistant's bottom card over whatever is on screen, or over the lock screen. */
@Composable
fun AssistOverlay(
    controller: AssistController,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    onTalk: () -> Unit,
    onOpenApp: () -> Unit
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(10.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.hermes.line, RoundedCornerShape(26.dp))
                // Taps inside the card must not reach the dismissing scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Header(controller, onDismiss)
            Transcript(controller, Modifier.weight(1f, fill = false))
            Footer(controller, onUnlock, onTalk, onOpenApp)
        }
    }
}

@Composable
private fun Header(controller: AssistController, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HermesBrandMark(size = 26)
        Text("Hermes", style = MaterialTheme.typography.titleMedium)
        if (controller.profile.isNotBlank()) StatusPill(controller.profile)
        if (controller.locked) Icon(Icons.Default.Lock, stringResource(R.string.assist_locked), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.assist_close)) }
    }
}

@Composable
private fun Transcript(controller: AssistController, modifier: Modifier) {
    val scroll = rememberScrollState()
    val streaming = controller.vm.streamingText
    LaunchedEffect(controller.exchanges.size, streaming.length, controller.phase) { scroll.animateScrollTo(scroll.maxValue) }
    Column(modifier.heightIn(max = 380.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        controller.exchanges.forEachIndexed { index, exchange ->
            val last = index == controller.exchanges.lastIndex
            Text(
                exchange.prompt,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.End)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.hermes.userBubble)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
            when {
                exchange.answer.isNotBlank() -> MarkdownLite(exchange.answer)
                last && controller.phase == Phase.THINKING && streaming.isNotBlank() -> MarkdownLite(streaming)
                last && controller.phase == Phase.THINKING -> ThinkingIndicator()
            }
        }
        if (controller.phase == Phase.THINKING && controller.vm.uiState.interaction != null) {
            Text(stringResource(R.string.assist_needs_confirmation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        status(controller)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun status(controller: AssistController): String? = when (controller.phase) {
    Phase.UNLOCK -> stringResource(R.string.assist_unlock_to_talk)
    Phase.NO_SERVER -> stringResource(R.string.assist_no_server)
    Phase.PERMISSION -> stringResource(R.string.assist_mic_needed)
    Phase.LISTENING -> stringResource(R.string.assist_listening)
    Phase.CONNECTING -> stringResource(R.string.assist_connecting)
    Phase.TRANSCRIBING -> stringResource(R.string.assist_transcribing)
    Phase.NOT_HEARD -> stringResource(R.string.assist_not_heard)
    Phase.UNREACHABLE -> stringResource(R.string.assist_unreachable)
    Phase.ERROR -> controller.error ?: stringResource(R.string.assist_unreachable)
    Phase.THINKING, Phase.ANSWERED -> null
}

@Composable
private fun Footer(controller: AssistController, onUnlock: () -> Unit, onTalk: () -> Unit, onOpenApp: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (controller.phase) {
            Phase.UNLOCK -> Button(onClick = onUnlock) { Icon(Icons.Default.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.assist_unlock)) }
            Phase.PERMISSION -> Button(onClick = onTalk) { Text(stringResource(R.string.assist_allow_mic)) }
            else -> {
                val review = controller.phase == Phase.THINKING && controller.vm.uiState.interaction != null
                TextButton(onClick = onOpenApp) {
                    Icon(if (controller.locked) Icons.Default.Lock else Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (review) R.string.assist_review else R.string.assist_open_app))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (controller.speaking) {
            IconButton(onClick = controller::stopSpeaking) { Icon(Icons.AutoMirrored.Filled.VolumeOff, stringResource(R.string.assist_stop_speaking)) }
        }
        if (controller.phase != Phase.UNLOCK && controller.phase != Phase.PERMISSION) MicButton(controller, onTalk)
    }
}

@Composable
private fun MicButton(controller: AssistController, onTalk: () -> Unit) {
    val listening = controller.phase == Phase.LISTENING
    val pulse by animateFloatAsState(if (listening) 1f + controller.level * 0.35f else 1f, label = "level")
    val enabled = !controller.busy || listening
    Box(contentAlignment = Alignment.Center) {
        if (listening) Box(Modifier.size(56.dp).scale(pulse).clip(CircleShape).background(MaterialTheme.hermes.accentTint))
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = .4f))
                .clickable(enabled = enabled) { if (listening) controller.stopListening() else onTalk() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (listening) Icons.Default.Stop else Icons.Default.Mic,
                stringResource(if (listening) R.string.assist_stop else R.string.assist_talk),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
