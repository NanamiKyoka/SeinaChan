package com.seina.chan.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.seina.chan.data.model.ChatMessage
import com.seina.chan.ui.components.MarkdownText
import com.seina.chan.ui.theme.AppShapes
import com.seina.chan.ui.theme.TextStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: ChatMessage,
    showToolCalls: Boolean = true,
    showReasoning: Boolean = true,
    showTimestamps: Boolean = false,
    hiddenToolNames: Set<String> = emptySet(),
    onImageClick: ((String) -> Unit)? = null,
    onQuote: ((ChatMessage) -> Unit)? = null,
    onResend: ((String) -> Unit)? = null,
    onEdit: ((ChatMessage) -> Unit)? = null,
    onBranch: ((ChatMessage) -> Unit)? = null
) {
    val isUser = message.role == "user"
    val effectiveShowToolCalls = showToolCalls && !isUser
    val effectiveShowReasoning = showReasoning && !isUser

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 思考链面板（仅 assistant 消息显示）
            if (effectiveShowReasoning && (message.isReasoning || message.reasoningText.isNotBlank())) {
                ReasoningPanel(
                    reasoningText = message.reasoningText,
                    isReasoning = message.isReasoning
                )
            }

            // 工具调用卡片列表（仅 assistant 消息显示，过滤隐藏的工具）
            if (effectiveShowToolCalls) {
                val visibleCalls = message.toolCalls.filter { it.name !in hiddenToolNames }
                visibleCalls.forEach { toolCall ->
                    key(toolCall.id) {
                        ToolCallCard(toolCall = toolCall)
                    }
                }
            }

            // 消息气泡
            var showMenu by remember { mutableStateOf(false) }
            var pressOffset by remember { mutableStateOf(Offset.Zero) }
            var showSelectionDialog by remember { mutableStateOf(false) }
            val clipboardManager = LocalClipboardManager.current
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .background(
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = AppShapes.lg
                    )
                    .padding(12.dp)
            ) {
                if (message.isStreaming && message.content.isEmpty() && message.imageUrl == null) {
                    TypingIndicator()
                } else {
                    val contentColumn: @Composable () -> Unit = {
                        Column {
                            if (message.imageUrl != null || isImageContent(message.content)) {
                                val imageModel = message.imageUrl ?: message.content
                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = "图片",
                                    modifier = Modifier
                                        .sizeIn(maxWidth = 240.dp, maxHeight = 240.dp)
                                        .clip(AppShapes.md)
                                        .then(
                                            if (onImageClick != null) {
                                                Modifier.clickable { onImageClick(imageModel) }
                                            } else Modifier
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (message.content.isNotBlank() && !isImageContent(message.content)) {
                                if (isUser) {
                                    Text(
                                        text = message.content,
                                        style = TextStyles.bodyMd,
                                        color = Color.White
                                    )
                                } else {
                                    MarkdownText(
                                        content = message.content,
                                        style = TextStyles.bodyMd,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                    contentColumn()
                }
                // 透明覆盖层承接长按手势，避免 ClickableText 拦截事件导致 onLongPress 不触发
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { offset ->
                                pressOffset = offset
                                showMenu = true
                            })
                        }
                )
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    offset = with(density) {
                        DpOffset(pressOffset.x.toDp(), pressOffset.y.toDp())
                    }
                ) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("全选") },
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("选择") },
                        onClick = {
                            showMenu = false
                            showSelectionDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("引用回复") },
                        enabled = onQuote != null,
                        onClick = {
                            onQuote?.invoke(message)
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重新发送") },
                        enabled = isUser && onResend != null,
                        onClick = {
                            onResend?.invoke(message.content)
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        enabled = isUser && onEdit != null,
                        onClick = {
                            onEdit?.invoke(message)
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("从此处分支新会话") },
                        enabled = onBranch != null,
                        onClick = {
                            onBranch?.invoke(message)
                            showMenu = false
                        }
                    )
                }

                if (showSelectionDialog) {
                    AlertDialog(
                        onDismissRequest = { showSelectionDialog = false },
                        title = { Text("选择文字") },
                        text = {
                            Box(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                            ) {
                                SelectionContainer {
                                    if (isUser) {
                                        Text(
                                            text = message.content,
                                            style = TextStyles.bodyMd
                                        )
                                    } else {
                                        MarkdownText(
                                            content = message.content,
                                            style = TextStyles.bodyMd,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSelectionDialog = false }) {
                                Text("关闭")
                            }
                        }
                    )
                }
            }

            // 时间戳
            if (showTimestamps && message.createdAt > 0) {
                Text(
                    text = formatTimestamp(message.createdAt),
                    style = TextStyles.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
                )
            }

            // 系统事件（如 Memory updated / Skill created 等）
            if (message.systemEvents.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.systemEvents.forEach { eventText ->
                        Text(
                            text = eventText,
                            style = TextStyles.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
private fun ReasoningPanel(
    reasoningText: String,
    isReasoning: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = AppShapes.md
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = !expanded }
        ) {
            Text(
                text = if (isReasoning) "思考中..." else "思考完成",
                style = TextStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isReasoning) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = reasoningText,
                    style = TextStyles.bodySm,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun isImageContent(content: String): Boolean {
    if (content.startsWith("data:image/")) return true
    if (content.startsWith("content://")) return true
    val cleanUrl = content.substringBefore("?").substringBefore("#")
    return cleanUrl.startsWith("http") &&
           listOf(".jpg", ".jpeg", ".png", ".gif", ".webp").any { cleanUrl.endsWith(it, ignoreCase = true) }
}
