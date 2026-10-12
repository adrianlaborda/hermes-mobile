package com.nousresearch.hermesmobile.ui.components

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nousresearch.hermesmobile.R
import org.json.JSONObject
import java.io.ByteArrayInputStream

@Composable
@SuppressLint("SetJavaScriptEnabled")
fun RichPreview(kind: String, source: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
                // Only packaged renderers run JavaScript. No model HTML, bridge, cookies, files or network.
                AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                                val uri = request.url
                                val path = uri.path.orEmpty().removePrefix("/render/")
                                if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net" || !uri.path.orEmpty().startsWith("/render/") ||
                                    !path.matches(Regex("[A-Za-z0-9_./-]+")) || ".." in path) return denied()
                                val mime = when (path.substringAfterLast('.')) { "js" -> "application/javascript"; "css" -> "text/css"; "html" -> "text/html"; "woff2" -> "font/woff2"; "woff" -> "font/woff"; "ttf" -> "font/ttf"; else -> return denied() }
                                return runCatching { WebResourceResponse(mime, "UTF-8", context.assets.open("render/$path")) }.getOrElse { denied() }
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                if (url == "https://appassets.androidplatform.net/render/viewer.html")
                                    view.evaluateJavascript("window.renderHermes(${JSONObject.quote(kind)},${JSONObject.quote(source.take(20001))});", null)
                            }
                            private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                        }
                        loadUrl("https://appassets.androidplatform.net/render/viewer.html")
                    }
                }, onRelease = { it.stopLoading(); it.destroy() })
            }
        }
    }
}
