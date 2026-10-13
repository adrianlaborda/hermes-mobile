package com.nousresearch.hermesmobile.ui.components

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nousresearch.hermesmobile.R
import kotlinx.coroutines.*
import java.io.File

@Composable
fun ChatAudioPlayer(path: String) {
    val owner = LocalArtifactOwner.current ?: return
    key(owner, path) { OwnedAudioPlayer(owner, path) }
}

@Composable
private fun OwnedAudioPlayer(owner: ArtifactOwner, path: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var cached by remember { mutableStateOf<File?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val attributes = remember { AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build() }
    val focus = remember {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if(change < 0) { runCatching { player?.pause() }; playing = false }
            }.build()
    }
    fun pause() { runCatching { player?.pause() }; playing = false; audioManager.abandonAudioFocusRequest(focus) }
    fun play() {
        if(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            player?.let { if(position >= duration) it.seekTo(0); it.start(); playing = true }
        }
    }
    DisposableEffect(owner, path, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_STOP) { pause(); job?.cancel() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); job?.cancel(); audioManager.abandonAudioFocusRequest(focus); player?.release(); player = null; cached?.delete() }
    }
    LaunchedEffect(playing) {
        while(playing) { position = player?.currentPosition ?: 0; delay(250) }
    }
    fun load() {
        busy = true; failed = false
        job = scope.launch {
            val file = File.createTempFile("hermes-chat-audio-", ".audio", context.cacheDir)
            var adopted = false
            try {
                withContext(Dispatchers.IO) {
                    owner.repo.rest.sessionMessagesPage(owner.session, owner.profile, limit = 1)
                    val mime = owner.repo.rest.downloadFile(owner.profile, path, owner.session, file, maxBytes = 32L * 1024 * 1024)
                    require(mime.startsWith("audio/") || mime.substringBefore(';') == "application/octet-stream")
                }
                ensureActive()
                val media = MediaPlayer()
                player = media; cached = file; adopted = true
                media.setAudioAttributes(attributes)
                media.setDataSource(file.path)
                media.setOnPreparedListener { duration = it.duration.coerceAtLeast(0); busy = false; play() }
                media.setOnCompletionListener { playing = false; position = duration; audioManager.abandonAudioFocusRequest(focus) }
                media.setOnErrorListener { _, _, _ ->
                    pause(); player?.release(); player = null; cached?.delete(); cached = null
                    busy = false; failed = true; true
                }
                media.prepareAsync()
            } catch(e: CancellationException) { busy = false; throw e }
            catch(e: Exception) { player?.release(); player = null; cached?.delete(); cached = null; busy = false; failed = true }
            finally { if(!adopted) file.delete() }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !busy, onClick = { if(playing) pause() else if(player != null) play() else load() }) {
                    if(busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if(playing) R.string.audio_pause else R.string.audio_play))
                }
                Slider(value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                    onValueChange = { position = it.toInt(); player?.seekTo(position) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f), enabled = player != null && !busy && duration > 0,
                    modifier = Modifier.weight(1f))
                Text("${audioTime(position)} / ${audioTime(duration)}", style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { owner.open(path) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.audio_open)) }
            }
            if(failed) Text(stringResource(R.string.audio_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
private fun audioTime(ms: Int): String = "${ms / 60000}:${((ms / 1000) % 60).toString().padStart(2, '0')}"
