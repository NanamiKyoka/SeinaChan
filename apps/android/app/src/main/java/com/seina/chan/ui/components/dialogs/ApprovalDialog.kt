package com.seina.chan.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seina.chan.data.remote.GatewayEvent.ApprovalRequest
import com.seina.chan.ui.components.SeinaButton
import com.seina.chan.ui.components.SeinaButtonVariant
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.seina.chan.R
import com.seina.chan.ui.theme.AppShapes
import com.seina.chan.ui.theme.Spacing
import com.seina.chan.ui.theme.TextStyles

@Composable
fun ApprovalDialog(
    request: ApprovalRequest?,
    onApprove: (Boolean) -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
    error: String? = null
) {
    if (request == null) return
    var allowPermanent by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = AppShapes.lg)
                .padding(Spacing.lg)
        ) {
            Text(
                text = "工具调用请求",
                style = TextStyles.bodyLg.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = "助手请求执行：${request.command}",
                style = TextStyles.bodyMd,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            if (request.description.isNotEmpty()) {
                Text(
                    text = request.description,
                    style = TextStyles.bodySm,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            if (request.allowPermanent) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row {
                    Checkbox(
                        checked = allowPermanent,
                        onCheckedChange = { allowPermanent = it }
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "记住此次选择",
                        style = TextStyles.bodyMd,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = TextStyles.bodySm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                SeinaButton(
                    text = "拒绝",
                    onClick = onReject,
                    variant = SeinaButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(Spacing.md))

                SeinaButton(
                    text = "批准",
                    onClick = { onApprove(allowPermanent) },
                    variant = SeinaButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
