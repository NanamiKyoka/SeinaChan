package com.seina.chan.ui.screens.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import com.seina.chan.data.model.ConnectionProfile
import com.seina.chan.ui.theme.AppShapes
import com.seina.chan.ui.theme.ErrorColor
import com.seina.chan.ui.theme.Spacing
import com.seina.chan.ui.theme.Success
import com.seina.chan.ui.theme.TextStyles

@Composable
fun ConnectScreen(
    onConnected: () -> Unit = {},
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profiles by viewModel.connectionProfiles.collectAsStateWithLifecycle()

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var menuExpandedFor by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTargetId by remember { mutableStateOf("") }
    var renameValue by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.navigateToChat.collect { success ->
            if (success) {
                onConnected()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "口袋星奈",
                style = TextStyles.displayLg
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            OutlinedTextField(
                value = uiState.ip,
                onValueChange = viewModel::onIpChange,
                label = { Text("服务器地址") },
                placeholder = { Text("127.0.0.1") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                singleLine = true,
                shape = AppShapes.md,
                colors = outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            OutlinedTextField(
                value = uiState.port,
                onValueChange = viewModel::onPortChange,
                label = { Text("端口") },
                placeholder = { Text("9119") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = AppShapes.md,
                colors = outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            OutlinedTextField(
                value = uiState.token,
                onValueChange = viewModel::onTokenChange,
                label = { Text("Token (可选)") },
                placeholder = { Text("留空则使用默认认证") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                singleLine = true,
                shape = AppShapes.md,
                colors = outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("测试连接")
            }

            when (val status = uiState.testStatus) {
                is TestStatus.Testing -> {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "测试中...",
                        style = TextStyles.bodySm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is TestStatus.Success -> {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "✅ ${status.message}",
                        style = TextStyles.bodySm,
                        color = Success,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is TestStatus.Error -> {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = status.message,
                        style = TextStyles.bodySm,
                        color = ErrorColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {}
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = uiState.error!!,
                    style = TextStyles.bodySm,
                    color = ErrorColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                TextButton(
                    onClick = viewModel::clearSavedConfig,
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorColor)
                ) {
                    Text("清除配置并重新输入")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Button(
                onClick = viewModel::connect,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = AppShapes.md
            ) {
                Text(if (uiState.isLoading) "连接中..." else "保存并连接")
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "配置 Hermes Dashboard 连接信息",
                style = TextStyles.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (profiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.xxl))
                Text(
                    text = "已保存的连接配置",
                    style = TextStyles.bodySm,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { viewModel.loadProfile(profile) },
                        onRename = {
                            renameTargetId = profile.id
                            renameValue = profile.name
                            showRenameDialog = true
                        },
                        onDelete = { viewModel.deleteProfile(profile.id) }
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            OutlinedButton(
                onClick = { showSaveDialog = true },
                enabled = !uiState.isLoading && uiState.ip.isNotBlank() && uiState.port.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shape = AppShapes.md
            ) {
                Text("保存当前配置")
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存配置") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("配置名称") },
                    placeholder = { Text("例如：本地服务器") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.md,
                    colors = outlinedTextFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saveName.isNotBlank()) {
                            viewModel.saveCurrentAsProfile(saveName.trim())
                            saveName = ""
                            showSaveDialog = false
                        }
                    },
                    enabled = saveName.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                renameValue = ""
            },
            title = { Text("重命名配置") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.md,
                    colors = outlinedTextFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameValue.isNotBlank()) {
                            viewModel.renameProfile(renameTargetId, renameValue.trim())
                            renameValue = ""
                            showRenameDialog = false
                        }
                    },
                    enabled = renameValue.isNotBlank()
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    renameValue = ""
                }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ConnectionProfile,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AppShapes.md,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = TextStyles.bodyMd,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = "${profile.ip}:${profile.port}",
                    style = TextStyles.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            expanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = ErrorColor) },
                        onClick = {
                            expanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.background,
    unfocusedContainerColor = MaterialTheme.colorScheme.background,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
)
