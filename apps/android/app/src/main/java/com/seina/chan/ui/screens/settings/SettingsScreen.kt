package com.seina.chan.ui.screens.settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.seina.chan.data.model.BUILTIN_THEMES
import com.seina.chan.data.model.FontPreset
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.seina.chan.data.model.ConnectionProfile
import com.seina.chan.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showToolsDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    // 返回按钮仅执行返回操作，避免任何副作用导致意外导航
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 显示设置
            item {
                SettingsSectionCard(
                    title = "显示设置",
                    icon = Icons.Filled.Visibility
                ) {
                    SwitchSettingItem(
                        title = "显示工具调用",
                        description = "在对话中展示工具调用过程",
                        checked = uiState.showToolCalls,
                        onCheckedChange = viewModel::setShowToolCalls,
                        icon = Icons.Filled.Construction
                    )
                    SwitchSettingItem(
                        title = "显示思考链",
                        description = "在对话中展示 AI 的思考过程",
                        checked = uiState.showReasoning,
                        onCheckedChange = viewModel::setShowReasoning,
                        icon = Icons.Filled.ExpandMore
                    )
                    SwitchSettingItem(
                        title = "显示消息时间戳",
                        description = "在消息旁显示发送时间",
                        checked = uiState.showTimestamps,
                        onCheckedChange = viewModel::setShowTimestamps,
                        icon = Icons.Filled.AccessTime
                    )
                    ToolVisibilitySettingItem(
                        hiddenToolNames = uiState.hiddenToolNames,
                        onHiddenToolsChange = viewModel::setHiddenToolNames,
                        customTools = uiState.customTools,
                        onAddCustomTool = { category, name -> viewModel.addCustomTool(category, name) },
                        onRemoveCustomTool = { category, name -> viewModel.removeCustomTool(category, name) },
                        icon = Icons.Filled.VisibilityOff
                    )
                    ClickableSettingItem(
                        title = "管理工具",
                        description = if (uiState.toolsError != null) "加载失败: ${uiState.toolsError}" else "查看和管理服务端工具",
                        onClick = {
                            viewModel.listTools()
                            showToolsDialog = true
                        },
                        icon = Icons.Filled.Build
                    )
                }
            }

            // 行为设置
            item {
                SettingsSectionCard(
                    title = "行为设置",
                    icon = Icons.Filled.Tune
                ) {
                    DropdownSettingItem(
                        title = "每页会话数",
                        description = "会话列表每页显示的会话数量",
                        value = "${uiState.pageSize}",
                        options = listOf("10", "20", "50"),
                        icon = Icons.Filled.FormatListNumbered,
                        onOptionSelected = {
                            viewModel.setPageSize(it.toInt())
                        }
                    )
                    SwitchSettingItem(
                        title = "自动展开思考链",
                        description = "自动展开 AI 的思考链内容",
                        checked = uiState.autoExpandReasoning,
                        onCheckedChange = viewModel::setAutoExpandReasoning,
                        icon = Icons.Filled.ExpandMore
                    )
                    SwitchSettingItem(
                        title = "自动展开工具结果",
                        description = "自动展开工具调用的返回结果",
                        checked = uiState.autoExpandTools,
                        onCheckedChange = viewModel::setAutoExpandTools,
                        icon = Icons.Filled.ExpandMore
                    )
                }
            }

            // 外观设置
            item {
                SettingsSectionCard(
                    title = "外观设置",
                    icon = Icons.Filled.Palette
                ) {
                    val themeLabel = when (uiState.themeMode) {
                        "light" -> "浅色"
                        "dark" -> "深色"
                        else -> "跟随系统"
                    }
                    DropdownSettingItem(
                        title = "主题模式",
                        description = "应用的主题外观",
                        value = themeLabel,
                        options = listOf("跟随系统", "浅色", "深色"),
                        optionValues = listOf("system", "light", "dark"),
                        icon = Icons.Filled.DarkMode,
                        onOptionSelected = { viewModel.setThemeMode(it) }
                    )
                    val currentTheme = BUILTIN_THEMES.find { it.id == uiState.activeThemeId }
                    var showThemePicker by remember { mutableStateOf(false) }
                    ClickableSettingItem(
                        title = "颜色主题",
                        description = currentTheme?.name ?: uiState.activeThemeId,
                        onClick = { showThemePicker = true },
                        icon = Icons.Filled.Palette
                    )
                    if (showThemePicker) {
                        AlertDialog(
                            onDismissRequest = { showThemePicker = false },
                            title = { Text("选择颜色主题") },
                            text = {
                                Column {
                                    BUILTIN_THEMES.forEach { theme ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.setActiveThemeId(theme.id)
                                                    showThemePicker = false
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            RadioButton(
                                                selected = theme.id == uiState.activeThemeId,
                                                onClick = {
                                                    viewModel.setActiveThemeId(theme.id)
                                                    showThemePicker = false
                                                }
                                            )
                                            Text(
                                                text = theme.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.padding(start = Spacing.sm)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showThemePicker = false }) {
                                    Text("关闭")
                                }
                            }
                        )
                    }
                    DropdownSettingItem(
                        title = "字体组合",
                        description = "界面使用的字体风格",
                        value = FontPreset.entries.find { it.id == uiState.fontPresetId }?.displayName ?: uiState.fontPresetId,
                        options = FontPreset.entries.map { it.displayName },
                        optionValues = FontPreset.entries.map { it.id },
                        icon = Icons.Filled.ExpandMore,
                        onOptionSelected = { viewModel.setFontPresetId(it) }
                    )
                }
            }

            // 连接设置
            item {
                SettingsSectionCard(
                    title = "连接设置",
                    icon = Icons.Filled.Link
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                    ) {
                        var ipText by remember { mutableStateOf(uiState.connectionIp) }
                        var portText by remember { mutableStateOf(uiState.connectionPort) }
                        var tokenText by remember { mutableStateOf(uiState.connectionToken) }

                        LaunchedEffect(uiState.connectionIp) { ipText = uiState.connectionIp }
                        LaunchedEffect(uiState.connectionPort) { portText = uiState.connectionPort }
                        LaunchedEffect(uiState.connectionToken) { tokenText = uiState.connectionToken }

                        OutlinedTextField(
                            value = ipText,
                            onValueChange = { ipText = it },
                            label = {
                                Text(
                                    text = "IP 地址",
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            label = {
                                Text(
                                    text = "端口",
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = tokenText,
                            onValueChange = { tokenText = it },
                            label = {
                                Text(
                                    text = "Token",
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Button(
                            onClick = {
                                viewModel.saveAndReconnect(ipText, portText, tokenText)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "保存并重连",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Button(
                            onClick = { 
                                viewModel.disconnect()
                                navController.navigate("connect") {
                                    popUpTo(0)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "断开当前连接",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        if (uiState.connectionProfiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.md))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Text(
                                text = "已保存的配置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            uiState.connectionProfiles.forEach { profile ->
                                ProfileCard(
                                    profile = profile,
                                    onClick = { viewModel.loadProfile(profile) }
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                            }
                        }
                    }
                }
            }

            // 模型设置
            item {
                SettingsSectionCard(
                    title = "模型设置",
                    icon = Icons.Filled.Tune
                ) {
                    if (uiState.availableModels.isNotEmpty()) {
                        val displayMap = uiState.availableModels.associate { it.modelId to it.display }
                        val currentDisplay = displayMap[uiState.selectedModel]
                            ?: uiState.selectedModel.ifBlank { "默认" }
                        DropdownSettingItem(
                            title = "当前模型",
                            description = "选择服务端使用的推理模型",
                            value = currentDisplay,
                            options = uiState.availableModels.map { it.display },
                            optionValues = uiState.availableModels.map { it.modelId },
                            icon = Icons.Filled.Tune,
                            onOptionSelected = { viewModel.setSelectedModel(it) }
                        )
                    } else {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "当前模型",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = uiState.selectedModel.ifBlank { "默认" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.fetchModelOptions() },
                            enabled = !uiState.isLoadingModels
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.padding(end = Spacing.xxs)
                            )
                            Text(
                                text = if (uiState.isLoadingModels) "加载中..." else "刷新模型列表",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    val modelError = uiState.modelError
                    if (modelError != null) {
                        Text(
                            text = modelError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }
    }

    if (showToolsDialog) {
        AlertDialog(
            onDismissRequest = { showToolsDialog = false },
            title = {
                Text("服务端工具")
            },
            text = {
                if (uiState.tools.isEmpty() && uiState.toolsError == null) {
                    Text("正在加载...")
                } else if (uiState.toolsError != null) {
                    Text(uiState.toolsError!!)
                } else if (uiState.tools.isEmpty()) {
                    Text("暂无可用工具")
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        uiState.tools.forEach { tool ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = tool.enabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.configureTool(tool.name, enabled)
                                    }
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tool.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (tool.description.isNotBlank()) {
                                        Text(
                                            text = tool.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showToolsDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

private val TOOL_CATEGORIES = listOf(
    "基础工具" to listOf(
        "terminal", "execute_code", "read_file", "write_file", "search_files", "patch",
        "process", "todo", "delegate_task", "cronjob", "memory",
        "session_search", "clarify", "send_message", "mixture_of_agents"
    ),
    "Web/搜索" to listOf("web_search", "web_extract", "x_search"),
    "音视频/图像" to listOf(
        "vision_analyze", "video_analyze", "video_generate", "image_generate",
        "text_to_speech"
    ),
    "浏览器" to listOf(
        "browser_navigate", "browser_click", "browser_type", "browser_scroll",
        "browser_snapshot", "browser_back", "browser_press", "browser_console",
        "browser_dialog", "browser_get_images", "browser_cdp", "browser_vision"
    ),
    "技能/插件" to listOf("skills_list", "skill_view", "skill_manage"),
    "Spotify" to listOf(
        "spotify_playback", "spotify_devices", "spotify_queue", "spotify_search",
        "spotify_playlists", "spotify_albums", "spotify_library"
    ),
    "Google Meet" to listOf("meet_join", "meet_status", "meet_transcript", "meet_leave", "meet_say"),
    "工具搜索" to listOf("tool_search", "tool_describe", "tool_call"),
    "Home Assistant" to listOf("ha_list_entities", "ha_get_state", "ha_list_services", "ha_call_service"),
    "看板" to listOf(
        "kanban_list", "kanban_show", "kanban_create", "kanban_comment",
        "kanban_complete", "kanban_block", "kanban_unblock", "kanban_heartbeat", "kanban_link"
    ),
    "飞书" to listOf(
        "feishu_doc_read", "feishu_drive_list_comments", "feishu_drive_add_comment",
        "feishu_drive_list_comment_replies", "feishu_drive_reply_comment"
    ),
    "社交" to listOf(
        "discord", "discord_admin", "yb_query_group_info", "yb_query_group_members",
        "yb_search_sticker", "yb_send_dm", "yb_send_sticker"
    ),
    "计算机控制" to listOf("computer_use")
)

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = Spacing.md,
                    top = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.xs
                )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = Spacing.xs)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ConnectionProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = "${profile.ip}:${profile.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolVisibilitySettingItem(
    hiddenToolNames: Set<String>,
    onHiddenToolsChange: (Set<String>) -> Unit,
    customTools: Set<String>,
    onAddCustomTool: (String, String) -> Unit,
    onRemoveCustomTool: (String, String) -> Unit,
    icon: ImageVector? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                text = "自定义隐藏工具链",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        supportingContent = {
            val hiddenCount = hiddenToolNames.size
            Text(
                text = if (hiddenCount == 0) "未隐藏任何工具" else "已隐藏 ${hiddenCount} 个工具",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier.clickable { showDialog = true }
    )

    if (showDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var localHidden by remember { mutableStateOf(hiddenToolNames) }
        // 自定义工具链输入状态
        var newToolName by remember { mutableStateOf("") }
        var newToolCategory by remember { mutableStateOf("自定义") }
        var showAddSection by remember { mutableStateOf(false) }

        // 解析自定义工具为分类映射
        val customToolsByCategory = remember(customTools) {
            customTools.groupBy({ entry ->
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2) parts[0] else "自定义"
            }, { entry ->
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2) parts[1] else entry
            })
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("隐藏指定工具链") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("搜索工具") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // 全选所有工具
                    val allToolNames = remember(customToolsByCategory) {
                        val names = mutableSetOf<String>()
                        TOOL_CATEGORIES.forEach { (_, tools) -> names.addAll(tools) }
                        customToolsByCategory.forEach { (_, tools) -> names.addAll(tools) }
                        names
                    }
                    val allSelected = allToolNames.isNotEmpty() && allToolNames.all { it in localHidden }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                localHidden = if (checked) localHidden + allToolNames else localHidden - allToolNames
                            }
                        )
                        Text(
                            text = "隐藏所有工具",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${localHidden.size} / ${allToolNames.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        val query = searchQuery.trim().lowercase()

                        // 内置工具分类
                        TOOL_CATEGORIES.forEach { (category, tools) ->
                            val filteredTools = if (query.isEmpty()) {
                                tools
                            } else {
                                tools.filter { it.lowercase().contains(query) }
                            }
                            if (filteredTools.isNotEmpty()) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Spacing.xs)
                                    ) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Checkbox(
                                            checked = filteredTools.isNotEmpty() && filteredTools.all { it in localHidden },
                                            onCheckedChange = { checked ->
                                                localHidden = if (checked) localHidden + filteredTools.toSet() else localHidden - filteredTools.toSet()
                                            }
                                        )
                                        Text(
                                            text = "全选",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                items(filteredTools, key = { "builtin|$category|$it" }) { tool ->
                                    ToolCheckRow(
                                        toolName = tool,
                                        isChecked = tool in localHidden,
                                        onCheckedChange = { checked ->
                                            localHidden = if (checked) localHidden + tool else localHidden - tool
                                        }
                                    )
                                }
                            }
                        }

                        // 自定义工具分类
                        if (customToolsByCategory.isNotEmpty()) {
                            customToolsByCategory.forEach { (category, tools) ->
                                val filteredTools = if (query.isEmpty()) {
                                    tools
                                } else {
                                    tools.filter { it.lowercase().contains(query) }
                                }
                                if (filteredTools.isNotEmpty()) {
                                    item {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = Spacing.xs)
                                        ) {
                                            Text(
                                                text = "$category（自定义）",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Checkbox(
                                                checked = filteredTools.isNotEmpty() && filteredTools.all { it in localHidden },
                                                onCheckedChange = { checked ->
                                                    localHidden = if (checked) localHidden + filteredTools.toSet() else localHidden - filteredTools.toSet()
                                                }
                                            )
                                            Text(
                                                text = "全选",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    items(filteredTools, key = { "custom|$category|$it" }) { tool ->
                                        ToolCheckRow(
                                            toolName = tool,
                                            isChecked = tool in localHidden,
                                            onCheckedChange = { checked ->
                                                localHidden = if (checked) localHidden + tool else localHidden - tool
                                            },
                                            onDelete = { onRemoveCustomTool(category, tool) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // 添加自定义工具链
                    if (showAddSection) {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "添加自定义工具链",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = newToolCategory,
                            onValueChange = { newToolCategory = it },
                            label = { Text("分类") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newToolName,
                                onValueChange = { newToolName = it },
                                label = { Text("工具名称") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            IconButton(
                                onClick = {
                                    val name = newToolName.trim()
                                    val cat = newToolCategory.trim().ifBlank { "自定义" }
                                    if (name.isNotBlank()) {
                                        onAddCustomTool(cat, name)
                                        newToolName = ""
                                    }
                                },
                                enabled = newToolName.trim().isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "添加",
                                    tint = if (newToolName.trim().isNotBlank()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    }
                                )
                            }
                        }
                    } else {
                        TextButton(onClick = { showAddSection = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.padding(end = Spacing.xxs)
                            )
                            Text("添加自定义工具链")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onHiddenToolsChange(localHidden)
                    showDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ToolCheckRow(
    toolName: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 4.dp)
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = toolName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(0.dp)
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun ClickableSettingItem(
    title: String,
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun DropdownSettingItem(
    title: String,
    description: String,
    value: String,
    options: List<String>,
    optionValues: List<String> = emptyList(),
    icon: ImageVector? = null,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val actualValues = if (optionValues.isEmpty()) options else optionValues

    Box {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            supportingContent = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            leadingContent = icon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    onClick = {
                        onOptionSelected(actualValues[index])
                        expanded = false
                    }
                )
            }
        }
    }
}
