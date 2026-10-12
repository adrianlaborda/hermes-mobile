package com.nousresearch.hermesmobile.features

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nousresearch.hermesmobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentPreview(export: ExportedFile) {
    if (export.mime == "application/pdf") {
        var page by remember(export) { mutableIntStateOf(0) }
        var pages by remember(export) { mutableIntStateOf(0) }
        var error by remember(export) { mutableStateOf<String?>(null) }
        val bitmap by produceState<Bitmap?>(null, export, page) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ParcelFileDescriptor.open(export.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { pdf ->
                            pages = pdf.pageCount
                            pdf.openPage(page.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))).use { p ->
                                val scale = 1200f / maxOf(p.width, p.height).coerceAtLeast(1)
                                Bitmap.createBitmap((p.width * scale).toInt().coerceAtLeast(1), (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(android.graphics.Color.WHITE)
                                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                }.onFailure { error = it.message }.getOrNull()
            }
        }
        bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.preview), Modifier.fillMaxWidth().heightIn(max = 480.dp)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            TextButton(enabled = page > 0, onClick = { page-- }) { Text(stringResource(R.string.page_previous)) }
            Text("${page + 1} / $pages", Modifier.padding(vertical = 12.dp))
            TextButton(enabled = page + 1 < pages, onClick = { page++ }) { Text(stringResource(R.string.page_next)) }
        }
    } else if (export.mime.startsWith("video/") || export.mime.startsWith("audio/")) {
        var player by remember(export) { mutableStateOf<VideoView?>(null) }
        DisposableEffect(export) { onDispose { player?.stopPlayback(); player = null } }
        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(owner, player) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player?.pause()
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
        AndroidView(modifier = Modifier.fillMaxWidth().height(if (export.mime.startsWith("audio/")) 120.dp else 260.dp), factory = { context ->
            VideoView(context).apply {
                setVideoPath(export.file.path)
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setOnPreparedListener { /* Playback starts only after the user taps play. */ }
                player = this
            }
        })
        TextButton(onClick = { player?.start() }) { Text(stringResource(R.string.ui_f36bd7b77a)) }
    }
}
