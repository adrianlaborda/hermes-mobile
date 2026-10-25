package com.nousresearch.hermesmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.ui.theme.hermes
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.*
import org.commonmark.ext.gfm.strikethrough.*
import org.commonmark.node.Text as MarkdownNodeText
import org.commonmark.node.Link as MarkdownLink
import org.commonmark.node.Image as MarkdownImage

object ChatMarkdown {
    private val parser = Parser.builder().extensions(listOf(TablesExtension.create(), StrikethroughExtension.create())).build()
    fun parse(text: String): Node = parser.parse(normalizeMath(text))

    // Lazy rows are disposed off-screen and recomposed when scrolled back. Reusing the
    // parsed tree keeps a row's first measured height equal to its final height, so
    // the transcript does not shift while the user scrolls through history.
    private val parsed = android.util.LruCache<String, Node>(256)
    fun cached(text: String): Node? = parsed.get(text)
    fun parseCached(text: String): Node = parsed.get(text) ?: parse(text).also { parsed.put(text, it) }
    internal fun normalizeMath(text: String): String {
        // Only transform display math outside code fences; preserve literal code exactly.
        var fenced = false
        var math = false
        return text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> { fenced = !fenced; line }
                !fenced && (trimmed == "$$" || trimmed == "\\[" || trimmed == "\\]") -> { math = !math; if (math) "```math" else "```" }
                else -> line
            }
        }
    }
    fun safeLink(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme?.lowercase() in setOf("http", "https", "mailto") &&
            (uri.scheme.equals("mailto", true) || !uri.host.isNullOrBlank()) && uri.userInfo == null
    }.getOrDefault(false)
}

private fun Node.children(): List<Node> = buildList {
    var child = firstChild
    while (child != null) { add(child); child = child.next }
}

@Composable
fun MarkdownLite(text: String) {
    // Parse outside the UI thread. Cancellation discards obsolete streaming projections.
    val document by produceState(ChatMarkdown.cached(text), text) {
        value = ChatMarkdown.cached(text) ?: withContext(Dispatchers.Default) { ChatMarkdown.parseCached(text) }
    }
    SelectionContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            document?.let { MarkdownBlocks(it, 0) } ?: Text(text)
        }
    }
}

@Composable
private fun MarkdownBlocks(parent: Node, depth: Int) {
    if (depth > 24) return
    parent.children().forEach { node ->
        when (node) {
            is Heading -> Text(inlineText(node), style = when (node.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }, fontWeight = FontWeight.SemiBold)
            is org.commonmark.node.Paragraph -> {
                // Gateway images render as cards below; their alt text is not repeated inline.
                val paragraph = inlineText(node)
                if (paragraph.isNotBlank()) Text(paragraph, style = MaterialTheme.typography.bodyLarge)
                fun images(n: Node, depth: Int = 0): List<MarkdownImage> = if (depth > 16) emptyList() else n.children().flatMap { if (it is MarkdownImage) listOf(it) else images(it, depth + 1) }
                fun audioLinks(n: Node, depth: Int = 0): List<String> = if(depth > 16) emptyList() else n.children().flatMap {
                    if(it is MarkdownLink) listOfNotNull(com.nousresearch.hermesmobile.data.ChatAudio.path(it.destination)) else audioLinks(it, depth + 1)
                }
                audioLinks(node).distinct().take(12).forEach { ChatAudioPlayer(it) }
                com.nousresearch.hermesmobile.data.ChatAudio.fromTool(renderMarkdownInline(node, Color.Unspecified, Color.Unspecified) {}.text)
                    .filter { it !in audioLinks(node) }.forEach { ChatAudioPlayer(it) }
                images(node).take(6).forEach { ChatArtifactPreview(it.destination, renderMarkdownInline(it, Color.Unspecified, Color.Unspecified) {}.text) }
            }
            is FencedCodeBlock -> CodeBlock(node.literal, node.info)
            is IndentedCodeBlock -> CodeBlock(node.literal, "")
            is BlockQuote -> Surface(
                color = MaterialTheme.hermes.codeBlock,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.hermes.codeBlockBorder)
            ) {
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { MarkdownBlocks(node, depth + 1) }
                }
            }
            is BulletList, is OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                node.children().forEachIndexed { index, item ->
                    Row {
                        Text(if (node is OrderedList) "${node.startNumber + index}. " else "• ", modifier = Modifier.widthIn(min = 24.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { MarkdownBlocks(item, depth + 1) }
                    }
                }
            }
            is ThematicBreak -> HorizontalDivider()
            is TableBlock -> {
                // Size columns to their content: measure the longest cell text per column.
                // GFM nests rows under TableHead / TableBody; flatten them so cells are cells.
                val rows = remember(node) {
                    node.children().flatMap { if (it is TableHead || it is TableBody) it.children() else listOf(it) }
                }
                val colWidths = remember(rows) {
                    val maxLens = IntArray(rows.maxOfOrNull { it.children().size } ?: 0)
                    rows.forEach { row ->
                        row.children().forEachIndexed { i, cell ->
                            if (i < maxLens.size) {
                                val len = renderMarkdownInline(cell, Color.Unspecified, Color.Unspecified) {}.text.length
                                maxLens[i] = maxOf(maxLens[i], len)
                            }
                        }
                    }
                    maxLens.map { len ->
                        // ~6.1dp per char at 13sp Inter, clamped to readable bounds.
                        (len * 6.1f).coerceIn(72f, 260f).dp
                    }
                }
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.hermes.line),
                    color = Color.Transparent
                ) {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    rows.forEachIndexed { rowIndex, row ->
                        val isHeader = row.children().firstOrNull().let { (it as? TableCell)?.isHeader == true }
                        Row(
                            Modifier
                                .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                                .background(if (isHeader) MaterialTheme.hermes.codeBlock else androidx.compose.ui.graphics.Color.Transparent)
                        ) {
                            row.children().forEachIndexed { cellIndex, cell ->
                                if (cellIndex > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.hermes.line))
                                Text(inlineText(cell), modifier = Modifier.width(colWidths.getOrElse(cellIndex) { 190.dp }).padding(horizontal = 10.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isHeader) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    textAlign = when ((cell as? TableCell)?.alignment) {
                                        TableCell.Alignment.RIGHT -> TextAlign.End
                                        TableCell.Alignment.CENTER -> TextAlign.Center
                                        else -> TextAlign.Start
                                    })
                            }
                        }
                        if (rowIndex < rows.lastIndex) HorizontalDivider(Modifier.width(colWidths.fold(0.dp) { acc, w -> acc + w } + (colWidths.size - 1).coerceAtLeast(0).dp),
                            color = MaterialTheme.hermes.line)
                    }
                }
                }
            }
            // Raw HTML is literal text: never execute scripts, fetch images or interpret tags.
            is HtmlBlock -> Text(node.literal, style = MaterialTheme.typography.bodyMedium)
            else -> MarkdownBlocks(node, depth + 1)
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String) {
    var showPreview by remember { mutableStateOf(false) }
    if (showPreview) RichPreview(language.trim().lowercase(), code) { showPreview = false }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    // Each copy restarts the 1.5 s feedback window, so a second tap is not cut short by the first one's timer.
    var copiedAt by remember { mutableIntStateOf(0) }
    LaunchedEffect(copiedAt) { if (copiedAt > 0) { delay(1_500); copied = false } }
    Surface(
        color = MaterialTheme.hermes.codeBlock,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.hermes.codeBlockBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    language.take(48),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(android.content.ClipData.newPlainText("Code", code).toClipEntry())
                            copied = true
                            copiedAt++
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = stringResource(if (copied) R.string.a24_copied else R.string.copy_code),
                        modifier = Modifier.size(16.dp),
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f)
                    )
                }
            }
            if (language.trim().lowercase() in setOf("mermaid", "math", "latex", "tex")) {
                TextButton(enabled = code.length <= 20000, onClick = { showPreview = true }) { Text(stringResource(R.string.preview)) }
            }
            Text(code.trimEnd('\n'), modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = com.nousresearch.hermesmobile.ui.theme.HermesMono))
        }
    }
}

@Composable
private fun inlineText(node: Node): AnnotatedString {
    val uriHandler = LocalUriHandler.current
    val artifactOwner = LocalArtifactOwner.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.surfaceVariant
    return remember(node, linkColor, codeColor, uriHandler, artifactOwner) {
        renderMarkdownInline(node, linkColor, codeColor, hideArtifactImages = artifactOwner != null) { link ->
            artifactPath(link)?.let { artifactOwner?.open?.invoke(it) } ?: uriHandler.openUri(link)
        }
    }
}

internal fun renderMarkdownInline(
    node: Node,
    linkColor: Color,
    codeColor: Color,
    hideArtifactImages: Boolean = false,
    onLink: (String) -> Unit
): AnnotatedString =
        buildAnnotatedString {
            fun visit(n: Node, depth: Int) {
                if (depth > 48) return
                fun children() { n.children().forEach { visit(it, depth + 1) } }
                when (n) {
                    is MarkdownNodeText -> append(n.literal)
                    is Code -> withStyle(SpanStyle(fontFamily = com.nousresearch.hermesmobile.ui.theme.HermesMono, background = codeColor)) { append(n.literal) }
                    is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { children() }
                    is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { children() }
                    is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { children() }
                    is SoftLineBreak -> append(" ")
                    is HardLineBreak -> append("\n")
                    is HtmlInline -> append(n.literal)
                    is MarkdownLink -> if (ChatMarkdown.safeLink(n.destination) || artifactPath(n.destination) != null) {
                        withLink(LinkAnnotation.Url(n.destination, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))) {
                            runCatching { onLink(n.destination) }
                        }) { children() }
                    } else children()
                    is MarkdownImage -> when {
                        depth == 0 -> children()
                        hideArtifactImages && artifactPath(n.destination) != null -> {}
                        else -> { append("[Image: "); children(); append("]") }
                    }
                    else -> children()
                }
            }
            visit(node, 0)
        }
