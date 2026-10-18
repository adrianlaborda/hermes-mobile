package com.nousresearch.hermesmobile.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.HermesRepository
import com.nousresearch.hermesmobile.features.MobileFiles
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.*

data class ArtifactOwner(val repo: HermesRepository, val profile: String, val session: String, val open: (String) -> Unit)
val LocalArtifactOwner = staticCompositionLocalOf<ArtifactOwner?> { null }

/** Decoded previews survive row disposal so scrolling back does not re-download or resize. */
private object ArtifactBitmapCache : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

internal fun artifactPath(destination: String): String? {
    val path = destination.removePrefix("sandbox:")
    return path.takeIf { it.startsWith('/') && !it.startsWith("//") && '\u0000' !in it && it.length <= 4096 && !it.startsWith("/api/") }
}

/**
 * Inline artifact card for images referenced by Hermes. Images auto-load when
 * they enter the viewport (no manual "Preview" tap), decode downsampled, and
 * offer Open / retry actions.
 */
@Composable
fun ChatArtifactPreview(destination: String, description: String) {
    com.nousresearch.hermesmobile.data.ChatAudio.path(destination)?.let { path ->
        ChatAudioPlayer(path)
        return
    }
    val owner = LocalArtifactOwner.current
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val cacheKey = owner?.let { "${it.repo.config.id}:${it.profile}:${it.session}:$destination" }
    var bitmap by remember(owner, destination) { mutableStateOf(cacheKey?.let { ArtifactBitmapCache.get(it) }) }
    var busy by remember(owner, destination) { mutableStateOf(false) }
    var error by remember(owner, destination) { mutableStateOf<String?>(null) }
    val path = artifactPath(destination)
    if (path == null || owner == null) {
        if (ChatMarkdown.safeLink(destination)) TextButton(onClick = { runCatching { uri.openUri(destination) } }) { Text(description.ifBlank { destination }.take(160)) }
        return
    }

    suspend fun load() {
        busy = true
        error = null
        try {
            bitmap = withContext(Dispatchers.IO) {
                owner.repo.rest.sessionMessagesPage(owner.session, owner.profile, limit = 1)
                val file = MobileFiles.target(context, path)
                try {
                    val mime = owner.repo.rest.downloadFile(owner.profile, path, owner.session, file, maxBytes = 8L * 1024 * 1024)
                    require(mime.startsWith("image/")) { "Use Open to view this artifact" }
                    requireNotNull(com.nousresearch.hermesmobile.data.ImageDecode.file(file.path, 1200)) { "Unsupported image" }
                } finally { file.delete() }
            }?.also { decoded -> cacheKey?.let { ArtifactBitmapCache.put(it, decoded) } }
        } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message }
        finally { busy = false }
    }

    // Auto-load: images should render on screen without a manual tap.
    LaunchedEffect(owner, destination) {
        if (bitmap == null) {
            try { load() } catch (_: CancellationException) {}
        }
    }

    val fileName = path.substringAfterLast('/')
    val title = description.trim().ifBlank { fileName }
    var viewer by remember(owner, destination) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.hermes.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            when {
                bitmap != null -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val image = requireNotNull(bitmap)
                    // Explicit height: aspectRatio + heightIn let tall images overflow onto the caption.
                    val ratio = (image.width.toFloat() / image.height.coerceAtLeast(1)).coerceIn(.5f, 3f)
                    Image(
                        image.asImageBitmap(),
                        title,
                        Modifier
                            .fillMaxWidth()
                            .height(minOf(maxWidth / ratio, 360.dp))
                            .clipToBounds()
                            .clickable(onClickLabel = stringResource(R.string.a22_view_image)) { viewer = true },
                        contentScale = ContentScale.Crop
                    )
                }
                busy -> Box(
                    Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.hermes.accentTint.copy(alpha = .4f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                else -> Box(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BrokenImage, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            error ?: title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.hermes.line)
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    title.take(80),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (error != null) {
                    TextButton(enabled = !busy, onClick = { scope.launch { load() } }) { Text(stringResource(R.string.a14_refresh)) }
                }
                IconButton(onClick = { owner.open(path) }) {
                    Icon(Icons.Default.OpenInNew, stringResource(R.string.preview), Modifier.size(18.dp))
                }
            }
        }
    }
    if (viewer) bitmap?.let { ZoomableImageViewer(it, title, onOpen = { viewer = false; owner.open(path) }, onDismiss = { viewer = false }) }
}

/** Full-screen viewer: pinch / pan to zoom, double-tap toggles 2.5x, tap the close button to leave. */
@Composable
private fun ZoomableImageViewer(bitmap: Bitmap, title: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                bitmap.asImageBitmap(),
                title,
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = ContentScale.Fit
            )
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .55f)).statusBarsPadding().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.a22_close), tint = Color.White) }
                Text(title, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onOpen) { Icon(Icons.Default.OpenInNew, stringResource(R.string.preview), tint = Color.White) }
            }
        }
    }
}
