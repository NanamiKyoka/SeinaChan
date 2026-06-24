package com.seina.chan.ui.screens.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.seina.chan.data.model.Session
import com.seina.chan.ui.theme.AppShapes
import com.seina.chan.ui.theme.Spacing
import com.seina.chan.ui.theme.TextStyles

@Composable
fun SessionListItem(
    session: Session,
    isSelected: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onUndo: () -> Unit = {},
    onCompress: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 2.dp else 0.dp,
                shape = AppShapes.md
            )
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    text = session.title ?: "新会话",
                    style = TextStyles.bodyMd,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                var showOverflow by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showOverflow = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("撤销上一轮") },
                            onClick = {
                                onUndo()
                                showOverflow = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("压缩上下文") },
                            onClick = {
                                onCompress()
                                showOverflow = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                onRename()
                                showOverflow = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                onDelete()
                                showOverflow = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = session.preview ?: "无消息",
                style = TextStyles.bodySm,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = session.lastActiveAt ?: "",
                    style = TextStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${session.messageCount}",
                    style = TextStyles.caption,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    }
}
