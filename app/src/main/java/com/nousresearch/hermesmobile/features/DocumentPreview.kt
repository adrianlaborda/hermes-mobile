package com.nousresearch.hermesmobile.features

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nousresearch.hermesmobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A PDF a page at a time: swipe sideways or use the arrows, with the page number between them. */
@Composable
private fun PdfPages(export: ExportedFile) {
    var error by remember(export) { mutableStateOf<String?>(null) }
    val pages by produceState(0, export) {
        value = withContext(Dispatchers.IO) {
            runCatching { ParcelFileDescriptor.open(export.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } } }
                .onFailure { error = it.message }.getOrDefault(0)
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (pages == 0) return
    val pager = rememberPagerState(pageCount = { pages })
    val scope = rememberCoroutineScope()
    HorizontalPager(pager, Modifier.fillMaxWidth().heightIn(max = 560.dp), beyondViewportPageCount = 1, pageSpacing = 12.dp) { page ->
        val bitmap by produceState<Bitmap?>(null, export, page) {
            value = withContext(Dispatchers.IO) { runCatching { renderPdfPage(export.file, page) }.onFailure { error = it.message }.getOrNull() }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.preview), Modifier.fillMaxWidth()) }
                ?: CircularProgressIndicator(Modifier.padding(48.dp))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = pager.currentPage > 0, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.page_previous))
        }
        Text("${pager.currentPage + 1} / $pages", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge)
        IconButton(enabled = pager.currentPage + 1 < pages, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.page_next))
        }
    }
}

private fun renderPdfPage(file: java.io.File, index: Int): Bitmap =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { pdf ->
            pdf.openPage(index.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))).use { p ->
                val scale = 1600f / maxOf(p.width, p.height).coerceAtLeast(1)
                Bitmap.createBitmap((p.width * scale).toInt().coerceAtLeast(1), (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

@Composable
fun DocumentPreview(export: ExportedFile) {
    if (export.mime == "application/pdf") {
        PdfPages(export)
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
