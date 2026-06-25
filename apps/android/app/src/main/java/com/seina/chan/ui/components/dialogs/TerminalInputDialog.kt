package com.seina.chan.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seina.chan.ui.components.SeinaButton
import com.seina.chan.ui.components.SeinaButtonVariant
import com.seina.chan.ui.components.SeinaTextField
import com.seina.chan.ui.theme.Spacing
import com.seina.chan.ui.theme.TextStyles

@Composable
fun TerminalInputDialog(
    prompt: String?,
    onRespond: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null
) {
    var input by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    text = "终端输入",
                    style = TextStyles.bodyLg,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                if (!prompt.isNullOrBlank()) {
                    Text(
                        text = prompt,
                        style = TextStyles.bodySm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
                SeinaTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "输入...",
                    singleLine = true
                )
                error?.let { err ->
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = TextStyles.bodySm
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    SeinaButton(
                        text = "取消",
                        onClick = onDismiss,
                        variant = SeinaButtonVariant.Secondary
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    SeinaButton(
                        text = "发送",
                        onClick = { onRespond(input) },
                        enabled = input.isNotBlank()
                    )
                }
            }
        }
    }
}
