package com.seina.chan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seina.chan.ui.theme.AppShapes
import com.seina.chan.ui.theme.CodeBg
import com.seina.chan.ui.theme.CodeText
import com.seina.chan.ui.theme.Primary
import com.seina.chan.ui.theme.TextStyles
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext


private sealed class MarkdownSegment {
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment()
    data class Paragraph(val spans: List<InlineSpan>) : MarkdownSegment()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownSegment()
    data class Heading(val level: Int, val text: String) : MarkdownSegment()
    data class ListItem(val text: String, val ordered: Boolean, val number: Int) : MarkdownSegment()
    data class Blockquote(val lines: List<String>) : MarkdownSegment()
    data class TaskItem(val text: String, val checked: Boolean) : MarkdownSegment()
}

private sealed class InlineSpan {
    data class Plain(val text: String) : InlineSpan()
    data class Bold(val text: String) : InlineSpan()
    data class Italic(val text: String) : InlineSpan()
    data class Code(val text: String) : InlineSpan()
    data class Link(val text: String, val url: String) : InlineSpan()
}


private val codeBlockPattern = Regex("""```([a-zA-Z0-9+-_]*)[ \t]*\n([\s\S]*?)```""")
private val linkPattern = Regex("""\[([^\]]+)\]\(([^)]+)\)""")


private fun parseMarkdown(content: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    var lastIndex = 0

    codeBlockPattern.findAll(content).forEach { match ->
        if (match.range.first > lastIndex) {
            val textBefore = content.substring(lastIndex, match.range.first)
            if (textBefore.isNotBlank()) {
                segments.addAll(parseBlocks(textBefore))
            }
        }
        val language = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        segments.add(MarkdownSegment.CodeBlock(language, code))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < content.length) {
        val remaining = content.substring(lastIndex)
        if (remaining.isNotBlank()) {
            segments.addAll(parseBlocks(remaining))
        }
    }

    return segments
}

private fun parseParagraphs(text: String): List<MarkdownSegment.Paragraph> {
    return text.split(Regex("\n{2,}"))
        .filter { it.isNotBlank() }
        .map { paragraph ->
            MarkdownSegment.Paragraph(parseInlineSpans(paragraph.trim()))
        }
}

private fun parseBlocks(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val lines = text.lines()
    var i = 0
    var plainTextStart = 0

    fun flushPlain(endLine: Int) {
        if (plainTextStart < endLine) {
            val plainText = lines.subList(plainTextStart, endLine).joinToString("\n")
            if (plainText.isNotBlank()) {
                segments.addAll(parseParagraphs(plainText))
            }
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isNotEmpty()) {
            // Heading
            val headingMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
            if (headingMatch != null) {
                flushPlain(i)
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2].trim()
                segments.add(MarkdownSegment.Heading(level, text))
                plainTextStart = i + 1
                i++
                continue
            }

            // Blockquote
            if (trimmed.startsWith("> ")) {
                flushPlain(i)
                val quoteLines = mutableListOf<String>()
                var j = i
                while (j < lines.size) {
                    val l = lines[j].trimStart()
                    if (l.startsWith("> ")) {
                        quoteLines.add(l.substring(2))
                        j++
                    } else {
                        break
                    }
                }
                segments.add(MarkdownSegment.Blockquote(quoteLines))
                plainTextStart = j
                i = j
                continue
            }

            // Task item
            val taskMatch = Regex("""^[-*]\s+\[([ xX])\]\s+(.*)$""").find(trimmed)
            if (taskMatch != null) {
                flushPlain(i)
                val checked = taskMatch.groupValues[1].lowercase() == "x"
                val text = taskMatch.groupValues[2]
                segments.add(MarkdownSegment.TaskItem(text, checked))
                plainTextStart = i + 1
                i++
                continue
            }

            // Unordered list item
            val unorderedMatch = Regex("""^[-*]\s+(.*)$""").find(trimmed)
            if (unorderedMatch != null) {
                flushPlain(i)
                val text = unorderedMatch.groupValues[1]
                segments.add(MarkdownSegment.ListItem(text, ordered = false, number = 0))
                plainTextStart = i + 1
                i++
                continue
            }

            // Ordered list item
            val orderedMatch = Regex("""^(\d+)\.\s+(.*)$""").find(trimmed)
            if (orderedMatch != null) {
                flushPlain(i)
                val number = orderedMatch.groupValues[1].toInt()
                val text = orderedMatch.groupValues[2]
                segments.add(MarkdownSegment.ListItem(text, ordered = true, number = number))
                plainTextStart = i + 1
                i++
                continue
            }

            // Table
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableStart = i
                var j = i + 1
                while (j < lines.size) {
                    val candidate = lines[j].trim()
                    if (candidate.startsWith("|") && candidate.endsWith("|")) {
                        j++
                    } else {
                        break
                    }
                }
                val tableText = lines.subList(tableStart, j).joinToString("\n")
                val table = parseTable(tableText)
                if (table != null) {
                    flushPlain(tableStart)
                    segments.add(table)
                    plainTextStart = j
                    i = j
                    continue
                }
            }
        }

        i++
    }

    flushPlain(lines.size)
    return segments
}

private fun parseTable(text: String): MarkdownSegment.Table? {
    val lines = text.lines()
    if (lines.size < 3) return null

    val separatorPattern = Regex("""\|\s*[-:]+\s*(\|\s*[-:]+\s*)*\|""")
    if (!separatorPattern.matches(lines[1].trim())) return null

    val headers = parseTableRow(lines[0]) ?: return null
    val rows = lines.subList(2, lines.size).mapNotNull { parseTableRow(it) }
    if (rows.isEmpty()) return null

    return MarkdownSegment.Table(headers = headers, rows = rows)
}

private fun parseTableRow(line: String): List<String>? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return null
    return trimmed.substring(1, trimmed.length - 1)
        .split("|")
        .map { it.trim() }
}

private fun parseInlineSpans(text: String): List<InlineSpan> {
    val spans = mutableListOf<InlineSpan>()
    var pos = 0
    val plainBuffer = StringBuilder()

    fun flushPlain() {
        if (plainBuffer.isNotEmpty()) {
            spans.add(InlineSpan.Plain(plainBuffer.toString()))
            plainBuffer.clear()
        }
    }

    while (pos < text.length) {
        if (text[pos] == '`') {
            flushPlain()
            val end = text.indexOf('`', pos + 1)
            if (end != -1) {
                spans.add(InlineSpan.Code(text.substring(pos + 1, end)))
                pos = end + 1
            } else {
                plainBuffer.append(text[pos])
                pos++
            }
        } else if (pos + 1 < text.length && text[pos] == '*' && text[pos + 1] == '*') {
            flushPlain()
            val end = text.indexOf("**", pos + 2)
            if (end != -1) {
                spans.add(InlineSpan.Bold(text.substring(pos + 2, end)))
                pos = end + 2
            } else {
                plainBuffer.append(text[pos])
                pos++
            }
        } else if (text[pos] == '*') {
            flushPlain()
            val end = text.indexOf('*', pos + 1)
            if (end != -1 && end != pos + 1) {
                spans.add(InlineSpan.Italic(text.substring(pos + 1, end)))
                pos = end + 1
            } else {
                plainBuffer.append(text[pos])
                pos++
            }
        } else if (text[pos] == '[') {
            val linkMatch = linkPattern.find(text, pos)
            if (linkMatch != null && linkMatch.range.first == pos) {
                flushPlain()
                spans.add(InlineSpan.Link(linkMatch.groupValues[1], linkMatch.groupValues[2]))
                pos = linkMatch.range.last + 1
            } else {
                plainBuffer.append(text[pos])
                pos++
            }
        } else {
            plainBuffer.append(text[pos])
            pos++
        }
    }

    flushPlain()
    return spans
}


private object CodeHighlighter {
    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "if", "else", "for", "while",
        "return", "import", "package", "data", "suspend", "when", "in", "is",
        "as", "true", "false", "null", "this", "super", "try", "catch",
        "finally", "throw", "interface", "enum", "sealed", "abstract", "open",
        "private", "public", "protected", "internal", "override", "lateinit",
        "companion", "const", "inline", "crossinline", "noinline", "reified",
        "out", "infix", "operator", "tailrec", "vararg", "typealias", "expect",
        "actual", "by", "where", "init", "get", "set", "field", "property",
        "file", "constructor", "delegate", "dynamic", "external", "annotation",
        "value", "context", "repeatable", "replaceWith", "Suppress", "OptIn",
        "Deprecated", "SuppressWarnings", "JvmName", "JvmStatic", "JvmOverloads",
        "JvmField", "Volatile", "Synchronized", "Transient", "Strictfp", "Native"
    )

    private val keywordPattern = Regex("""\b(${keywords.joinToString("|")})\b""")
    private val numberPattern = Regex("""\d+(\.\d+)?""")

    fun highlight(code: String, language: String): AnnotatedString {
        return buildAnnotatedString {
            var pos = 0
            while (pos < code.length) {
                val remaining = code.substring(pos)
                var matched = false

                if (!matched && (code[pos] == '"' || code[pos] == '\'' || code[pos] == '`')) {
                    val quote = code[pos]
                    var i = pos + 1
                    while (i < code.length) {
                        if (code[i] == '\\' && i + 1 < code.length) {
                            i += 2
                        } else if (code[i] == quote) {
                            i++
                            break
                        } else {
                            i++
                        }
                    }
                    withStyle(SpanStyle(color = Color(0xFF4CAF50))) {
                        append(code.substring(pos, i))
                    }
                    pos = i
                    matched = true
                }

                if (!matched && remaining.startsWith("/*")) {
                    val end = code.indexOf("*/", pos + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(color = Color.Gray)) {
                            append(code.substring(pos, end + 2))
                        }
                        pos = end + 2
                    } else {
                        withStyle(SpanStyle(color = Color.Gray)) {
                            append(code.substring(pos))
                        }
                        pos = code.length
                    }
                    matched = true
                }

                if (!matched && remaining.startsWith("//")) {
                    val end = code.indexOf('\n', pos)
                    if (end != -1) {
                        withStyle(SpanStyle(color = Color.Gray)) {
                            append(code.substring(pos, end))
                        }
                        pos = end
                    } else {
                        withStyle(SpanStyle(color = Color.Gray)) {
                            append(code.substring(pos))
                        }
                        pos = code.length
                    }
                    matched = true
                }

                if (!matched) {
                    val numMatch = numberPattern.find(remaining)
                    if (numMatch != null && numMatch.range.first == 0) {
                        withStyle(SpanStyle(color = Color(0xFF2196F3))) {
                            append(numMatch.value)
                        }
                        pos += numMatch.value.length
                        matched = true
                    }
                }

                if (!matched) {
                    val kwMatch = keywordPattern.find(remaining)
                    if (kwMatch != null && kwMatch.range.first == 0) {
                        withStyle(SpanStyle(color = Color(0xFFCC785C))) {
                            append(kwMatch.value)
                        }
                        pos += kwMatch.value.length
                        matched = true
                    }
                }

                if (!matched) {
                    append(code[pos])
                    pos++
                }
            }
        }
    }
}

/**
 * 轻量级 Markdown 渲染组件，支持：
 * - 代码块（``` ... ```）：深色背景 + 等宽字体
 * - 行内代码（`code`）：等宽字体 + 浅色背景
 * - 粗体（**text**）
 * - 斜体（*text*）
 * - 链接（[text](url)）：下划线 + 主题色
 * - 表格（| a | b |）
 * - 标题（# H1 - ###### H6）
 * - 无序/有序列表（- / * / 1.）
 * - 引用块（>）
 * - 任务列表（- [ ] / - [x]）
 */
@Composable
fun MarkdownText(
    content: String,
    style: TextStyle = TextStyles.bodyMd,
    color: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier
) {
    val segments = remember(content) { parseMarkdown(content) }

    if (segments.isEmpty()) {
        Text(text = content, style = style, color = color, modifier = modifier)
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.CodeBlock -> CodeBlockView(segment)
                is MarkdownSegment.Paragraph -> {
                    val annotatedString = buildInlineAnnotatedString(segment.spans, color)
                    val context = LocalContext.current
                    ClickableText(
                        text = annotatedString,
                        style = style.copy(color = color),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    val url = annotation.item
                                    if (url.startsWith("http://") || url.startsWith("https://")) {
                                        try {
                                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                        }
                    )
                }
                is MarkdownSegment.Table -> TableView(segment)
                is MarkdownSegment.Heading -> HeadingView(segment)
                is MarkdownSegment.ListItem -> ListView(segment)
                is MarkdownSegment.Blockquote -> BlockquoteView(segment)
                is MarkdownSegment.TaskItem -> TaskListView(segment)
            }
        }
    }
}

@Composable
private fun CodeBlockView(codeBlock: MarkdownSegment.CodeBlock) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = CodeBg,
                shape = AppShapes.md
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (codeBlock.language.isNotBlank()) {
                Text(
                    text = codeBlock.language,
                    style = TextStyles.caption,
                    color = CodeText.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            val highlighted = remember(codeBlock.code, codeBlock.language) {
                CodeHighlighter.highlight(codeBlock.code, codeBlock.language)
            }
            Text(
                text = highlighted,
                style = TextStyles.code,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val context = LocalContext.current
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("code", codeBlock.code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "复制代码",
                tint = CodeText.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TableView(table: MarkdownSegment.Table) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = AppShapes.md
            )
            .padding(8.dp)
    ) {
        val columnCount = table.headers.size
        val minTableWidth = (columnCount * 80).dp
        val tableWidth = maxWidth.coerceAtLeast(minTableWidth)

        Box(
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.width(tableWidth)) {
                // Header row
                Row(modifier = Modifier.fillMaxWidth()) {
                    table.headers.forEach { header ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = header,
                                style = TextStyles.bodyMd.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Divider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                // Data rows
                table.rows.forEachIndexed { index, row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cell,
                                    style = TextStyles.bodyMd,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    if (index < table.rows.size - 1) {
                        Divider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadingView(heading: MarkdownSegment.Heading) {
    val fontSize = when (heading.level) {
        1 -> 28.sp
        2 -> 24.sp
        3 -> 20.sp
        4 -> 18.sp
        5 -> 16.sp
        else -> 14.sp
    }
    Text(
        text = heading.text,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun ListView(listItem: MarkdownSegment.ListItem) {
    val prefix = if (listItem.ordered) {
        "${listItem.number}. "
    } else {
        "• "
    }
    Text(
        text = prefix + listItem.text,
        style = TextStyles.bodyMd,
        modifier = Modifier
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun BlockquoteView(blockquote: MarkdownSegment.Blockquote) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Primary)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            blockquote.lines.forEach { line ->
                Text(
                    text = line,
                    style = TextStyles.bodyMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TaskListView(taskItem: MarkdownSegment.TaskItem) {
    val prefix = if (taskItem.checked) "☑ " else "☐ "
    Text(
        text = prefix + taskItem.text,
        style = TextStyles.bodyMd,
        modifier = Modifier
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onBackground
    )
}

private fun buildInlineAnnotatedString(spans: List<InlineSpan>, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        for (span in spans) {
            when (span) {
                is InlineSpan.Plain -> append(span.text)
                is InlineSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(span.text)
                }
                is InlineSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(span.text)
                }
                is InlineSpan.Code -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = baseColor.copy(alpha = 0.12f),
                    )
                ) {
                    append("\u2009${span.text}\u2009")
                }
                is InlineSpan.Link -> {
                    pushStringAnnotation(tag = "URL", annotation = span.url)
                    withStyle(
                        SpanStyle(
                            color = Primary,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(span.text)
                    }
                    pop()
                }
            }
        }
    }
}
