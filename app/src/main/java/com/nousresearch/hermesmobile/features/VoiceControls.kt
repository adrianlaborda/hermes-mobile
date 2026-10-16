package com.nousresearch.hermesmobile.features

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class VoiceActions(
    val recording: Boolean, val busy: Boolean, val speaking: Boolean,
    val status: String?, val canSpeak: Boolean,
    val dictate: () -> Unit, val speak: () -> Unit, val cancel: () -> Unit,
    /** Reads arbitrary text (e.g. one assistant message); tapping again while speaking stops. */
    val speakText: (String) -> Unit = {}
)

@Composable
fun VoiceControls(c:ParityController,onTranscript:(String)->Unit,textForSpeech:String,
                  content: (@Composable (VoiceActions) -> Unit)? = null) {
    val context=LocalContext.current
    val msgPreparingMic=stringResource(R.string.a22_voice_preparing_mic)
    val msgRecording=stringResource(R.string.a22_voice_recording)
    val msgPermission=stringResource(R.string.a22_voice_permission)
    val msgPreparingSpeech=stringResource(R.string.a22_voice_preparing_speech)
    val msgSpeaking=stringResource(R.string.chat_speaking)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scope=rememberCoroutineScope()
    val transcriptCallback by rememberUpdatedState(onTranscript)
    val lease=remember(c){"mobile:${UUID.randomUUID()}"}
    var recorder by remember(c){mutableStateOf<MediaRecorder?>(null)}
    var recording by remember(c){mutableStateOf<File?>(null)}
    var busy by remember(c){mutableStateOf(false)}
    var speaking by remember(c){mutableStateOf(false)}
    var status by remember(c){mutableStateOf<String?>(null)}
    var voiceJob by remember(c){mutableStateOf<Job?>(null)}
    // The device voice (Settings → Voice assistant → Voice), created on first use.
    val deviceSpeech = remember(c){ lazy { com.nousresearch.hermesmobile.assist.AndroidSpeech(context, android.media.AudioAttributes.USAGE_MEDIA) } }
    suspend fun lease(kind:String,active:Boolean){try{c.api("/api/audio/$kind-lease","POST",json("lease" to lease,"active" to active),180)}catch(e:com.nousresearch.hermesmobile.data.HermesHttpException){if(e.status !in listOf(404,405,501))throw e}}
    fun releaseInBackground(){CoroutineScope(Dispatchers.IO).launch {withTimeoutOrNull(10_000){runCatching{lease("stt",false)};runCatching{lease("tts",false)}}}}
    fun cancel(){if(deviceSpeech.isInitialized())deviceSpeech.value.stop();voiceJob?.cancel();recorder?.let{runCatching{it.stop()};it.release()};recorder=null;recording?.delete();recording=null;busy=false;speaking=false;status=null;releaseInBackground()}
    fun finish(){
        val active=recorder ?: return
        val audio=recording ?: return
        recorder=null;recording=null;busy=true
        voiceJob=scope.launch {
            try {
                runCatching{active.stop()}.getOrElse{throw IllegalStateException("Recording was too short")}
                val data=withContext(Dispatchers.IO){require(audio.length()<=8*1024*1024){"Recording is too large"};"data:audio/mp4;base64,"+Base64.encodeToString(audio.readBytes(),Base64.NO_WRAP)}
                val result=c.api("/api/audio/transcribe","POST",json("data_url" to data,"mime_type" to "audio/mp4"),180)
                transcriptCallback(result.optString("transcript"));status=null
            }catch(e:CancellationException){throw e}catch(e:Throwable){status=e.message}
            finally {active.release();audio.delete();withContext(NonCancellable){withTimeoutOrNull(10_000){runCatching{lease("stt",false)}}};busy=false}
        }
    }
    fun start(){
        if(busy || recorder!=null)return
        busy=true;status=msgPreparingMic
        voiceJob=scope.launch {
            var file:File?=null
            try {
                lease("stt",true)
                ensureActive()
                file=File.createTempFile("hermes-voice-",".m4a",context.cacheDir)
                @Suppress("DEPRECATION") val rec=if(android.os.Build.VERSION.SDK_INT>=31)MediaRecorder(context)else MediaRecorder()
                recorder=rec;recording=file
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioSamplingRate(16000);rec.setAudioEncodingBitRate(64000)
                rec.setMaxDuration(120_000);rec.setMaxFileSize(8L*1024*1024)
                rec.setOutputFile(file.path)
                rec.setOnInfoListener{_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)finish()}
                rec.prepare();rec.start();status=msgRecording
            }catch(e:CancellationException){file?.delete();throw e}catch(e:Throwable){cancel();status=e.message}
            finally{if(recorder==null)withContext(NonCancellable){withTimeoutOrNull(10_000){runCatching{lease("stt",false)}}};busy=false}
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)start()else status=msgPermission}
    DisposableEffect(c,lifecycle){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)cancel()}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);cancel();if(deviceSpeech.isInitialized())deviceSpeech.value.shutdown()}
    }
    val dictate: () -> Unit = {
        if(recorder!=null) finish()
        else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val speakText: (String) -> Unit = { textToSpeak ->
                if(speaking)cancel()else if(textToSpeak.isNotBlank() && com.nousresearch.hermesmobile.assist.AssistSettings(context).deviceVoice) {
                    speaking=true;status=msgSpeaking
                    voiceJob=scope.launch {
                        try { deviceSpeech.value.speak(textToSpeak); status=null }
                        catch(e:CancellationException){throw e}catch(e:Throwable){status=e.message}
                        finally{speaking=false}
                    }
                } else if(textToSpeak.isNotBlank()) {
                    speaking=true;status=msgPreparingSpeech
                    voiceJob=scope.launch {
                        var file:File?=null;var player:MediaPlayer?=null
                        try {
                            lease("tts",true)
                            val result=c.api("/api/audio/speak","POST",json("text" to textToSpeak.take(40_000)),600)
                            val data=result.optString("data_url")
                            require(data.startsWith("data:audio/") && ";base64," in data && data.length<=32*1024*1024){"Unsupported or oversized speech response"}
                            file=withContext(Dispatchers.IO){File.createTempFile("hermes-speech-",".audio",context.cacheDir).apply{writeBytes(Base64.decode(data.substringAfter(','),Base64.DEFAULT))}}
                            player=MediaPlayer();player.setDataSource(file.path);status=msgSpeaking
                            val mp=player
                            suspendCancellableCoroutine<Unit>{continuation->
                                mp.setOnPreparedListener{it.start()};mp.setOnCompletionListener{if(continuation.isActive)continuation.resume(Unit)}
                                mp.setOnErrorListener{_,_,_->if(continuation.isActive)continuation.resumeWithException(IllegalStateException("Audio playback failed"));true}
                                mp.prepareAsync();continuation.invokeOnCancellation{runCatching{mp.stop()}}
                            };status=null
                        }catch(e:CancellationException){throw e}catch(e:Throwable){status=e.message}
                        finally{player?.release();file?.delete();withContext(NonCancellable){withTimeoutOrNull(10_000){runCatching{lease("tts",false)}}};speaking=false}
                    }
                }
    }
    val speak: () -> Unit = { speakText(textForSpeech) }
    val actions = VoiceActions(recorder!=null, busy, speaking, status,
        recorder==null && !busy && textForSpeech.isNotBlank(), dictate, speak, ::cancel, speakText)
    if(content != null) content(actions) else Column {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=!busy && !speaking,onClick=dictate) {
                Icon(if(recorder==null)Icons.Default.Mic else Icons.Default.Stop,null)
                Text(stringResource(if(recorder==null) R.string.chat_dictate else R.string.chat_stop))
            }
            TextButton(enabled=actions.canSpeak || speaking,onClick=speak) {
                Icon(if(speaking)Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,null)
                Text(stringResource(if(speaking) R.string.chat_stop else R.string.chat_read_aloud))
            }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
    }

}

@Composable
fun VoicePage(c:ParityController){
    var text by remember(c){mutableStateOf("")}
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        FeatureCard(stringResource(R.string.ui_87bf2bc085), "Chained mode: Android microphone → gateway transcription → chat → gateway speech."){
            Text(stringResource(R.string.ui_9bc8e048c1))
            c.data["voice"]?.let{Text(stringResource(R.string.i18n_voice_status, it.optString("provider").ifBlank {it.optString("status","available")}),style=MaterialTheme.typography.bodySmall)}
            c.unavailable["voice"]?.let{Text(shown(it),style=MaterialTheme.typography.bodySmall)}
        }
        OutlinedTextField(text,{text=it},label={Text(stringResource(R.string.ui_91a13515e5))},modifier=Modifier.fillMaxWidth().weight(1f))
        VoiceControls(c,{text=it},text)
        Text(stringResource(R.string.ui_a96cd7674d),style=MaterialTheme.typography.bodySmall)
    }
}
