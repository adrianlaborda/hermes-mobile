package com.nousresearch.hermesmobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Incoming content is staged locally; accepting it only modifies a chosen chat's draft. */
data class IncomingShare(val text: String, val files: List<ComposerAttachment>)

object ShareImport {
    suspend fun read(context: Context, intent: Intent): IncomingShare = withContext(Dispatchers.IO) {
        require(intent.action in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE))
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        require(text.length <= 100_000) { "Shared text is too long (maximum 100,000 characters)" }
        val uris = if (intent.action == Intent.ACTION_SEND_MULTIPLE)
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        require(uris.size <= 6) { "Share up to six files at once" }
        val imported = mutableListOf<ComposerAttachment>()
        try {
            for (uri in uris.distinct()) {
                ensureActive()
                require(uri.scheme == "content") { "Only Android document providers are accepted" }
                // Do not let another app use Hermes as a proxy to read Hermes's own exports.
                require(uri.authority != "${context.packageName}.files") { "Select the original document to attach it" }
                var name = "attachment"
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) name = it.getString(0).orEmpty().substringAfterLast('/').substringAfterLast('\\').take(200).ifBlank { "attachment" }
                }
                val file = File(File(context.filesDir, "incoming").apply { mkdirs() }, UUID.randomUUID().toString())
                try {
                    context.contentResolver.openInputStream(uri)?.use { source ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(32768)
                            var total = 0L
                            while (true) {
                                ensureActive()
                                val n = source.read(buffer)
                                if (n < 0) break
                                total += n
                                require(total <= 25L * 1024 * 1024) { "$name exceeds 25 MB" }
                                output.write(buffer, 0, n)
                            }
                        }
                    } ?: error("Cannot open $name")
                    imported += ComposerAttachment(UUID.randomUUID().toString(), Uri.fromFile(file).toString(), name,
                        context.contentResolver.getType(uri) ?: "application/octet-stream", file.length())
                } catch (t: Throwable) { file.delete(); throw t }
            }
            require(text.isNotBlank() || imported.isNotEmpty()) { "Nothing to share" }
            IncomingShare(text, imported)
        } catch (t: Throwable) { discard(context, IncomingShare("", imported)); throw t }
    }
    fun discard(context: Context, share: IncomingShare) {
        val root = File(context.filesDir, "incoming").canonicalFile
        share.files.forEach { attachment ->
            runCatching { File(Uri.parse(attachment.uri).path.orEmpty()).canonicalFile.takeIf { it.parentFile == root }?.delete() }
        }
    }
}
