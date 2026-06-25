package com.seina.chan.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seina.chan.data.remote.GatewayEvent.ClarifyRequest
import com.seina.chan.ui.components.SeinaButton
import com.seina.chan.ui.components.SeinaButtonVariant
import com.seina.chan.ui.components.SeinaTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.seina.chan.R
import com.seina.chan.ui.theme.AppShapes
import com.seina.chan.ui.theme.Spacing
import com.seina.chan.ui.theme.TextStyles

@Composable
fun ClarifyDialog(
    request: ClarifyRequest?,
    onRespond: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null
) {
    if (request == null) return

    var text by remember { mutableStateOf("") }

    LaunchedEffect(request.id) {
        text = ""
    }

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
                text = "需要澄清",
                style = TextStyles.bodyLg.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = request.question,
                style = TextStyles.bodyMd,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            SeinaTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "请输入回复...",
                modifier = Modifier.fillMaxWidth()
            )

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
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    variant = SeinaButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(Spacing.md))

                SeinaButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        onRespond(text)
                    },
                    variant = SeinaButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
