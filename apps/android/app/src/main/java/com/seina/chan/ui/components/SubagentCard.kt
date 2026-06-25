package com.seina.chan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seina.chan.ui.screens.chat.SubagentDetail
import com.seina.chan.ui.theme.AppShapes
import com.seina.chan.ui.theme.Spacing
import com.seina.chan.ui.theme.TextStyles

@Composable
fun SubagentCard(
    subagent: SubagentDetail,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isComplete = subagent.status != null
    val isError = subagent.status in listOf("error", "failed", "timeout")
    val isRunning = !isComplete

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = AppShapes.md,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isError -> Icons.Default.Error
                        isComplete -> Icons.Default.CheckCircle
                        else -> Icons.Default.Android
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = when {
                        isError -> MaterialTheme.colorScheme.error
                        isComplete -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "子代理 ${subagent.taskIndex + 1}/${subagent.taskCount}",
                        style = TextStyles.label,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subagent.goal.isNotBlank()) {
                        Text(
                            text = subagent.goal,
                            style = TextStyles.bodySm,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 1
                        )
                    }
                }
                if (!isComplete) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(20.dp)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                if (subagent.thinking.isNotBlank()) {
                    Text(
                        text = "思考: ${subagent.thinking}",
                        style = TextStyles.bodySm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                subagent.progressMessages.forEach { msg ->
                    Text(
                        text = msg,
                        style = TextStyles.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isComplete) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "状态: ${subagent.status} | 耗时: ${subagent.durationSeconds?.let { "%.1fs".format(it) } ?: "N/A"}",
                        style = TextStyles.caption,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (subagent.summary.isNotBlank()) {
                        Text(
                            text = subagent.summary,
                            style = TextStyles.bodySm,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
