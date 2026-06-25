package com.seina.chan.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seina.chan.data.remote.GatewayEvent
import com.seina.chan.ui.components.SeinaTextField
import com.seina.chan.ui.components.VerticalScrollbar
import com.seina.chan.ui.components.dialogs.ApprovalDialog
import com.seina.chan.util.FileLogger
import com.seina.chan.ui.components.dialogs.ClarifyDialog
import com.seina.chan.ui.components.dialogs.SecretDialog
import com.seina.chan.ui.components.dialogs.SudoDialog
import com.seina.chan.ui.components.dialogs.ImagePreviewDialog
import com.seina.chan.ui.screens.sessions.SessionListScreen
import com.seina.chan.ui.theme.Spacing
import com.seina.chan.ui.theme.TextStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    sessionId: String,
    onBack: () -> Unit,
    onReconfigure: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val slashCommands by viewModel.slashCommands.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()
    val isStreaming = uiState.messages.any { it.isStreaming }
    var currentSessionId by rememberSaveable { mutableStateOf(sessionId) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var backPressedTime by remember { mutableStateOf(0L) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val pendingApproval = remember { mutableStateOf<GatewayEvent.ApprovalRequest?>(null) }
    val pendingClarify = remember { mutableStateOf<GatewayEvent.ClarifyRequest?>(null) }
    val pendingSecret = remember { mutableStateOf<GatewayEvent.SecretRequest?>(null) }
    val pendingSudo = remember { mutableStateOf<GatewayEvent.SudoRequest?>(null) }
    var previewImageUri by remember { mutableStateOf<String?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // 仅在用户处于消息列表底部时自动滚动；若用户手动上滑查看历史，则停止自动跟随
    val autoScrollEnabled by remember {
        derivedStateOf { !listState.canScrollForward }
    }

    // 用于计算未读消息数：autoScrollEnabled 时同步 lastSeenMessageCount
    var lastSeenMessageCount by remember { mutableStateOf(uiState.messages.size) }
    LaunchedEffect(uiState.messages.size, autoScrollEnabled) {
        if (autoScrollEnabled) {
            lastSeenMessageCount = uiState.messages.size
        }
    }
    val unreadCount = (uiState.messages.size - lastSeenMessageCount).coerceAtLeast(0)

    // 下拉刷新：loadMessages 内部启动协程并管理 isLoading，观察其完成以清除刷新指示器
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    // 新消息到达时（条数变化），仅在底部自动滚动
    LaunchedEffect(uiState.messages.size) {
        if (autoScrollEnabled && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // 流式回复内容增长时（最后一条消息长度变化），仅在底部自动跟随
    LaunchedEffect(uiState.messages.lastOrNull()?.content?.length) {
        if (autoScrollEnabled && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // 输入法弹出时始终滚动到底部（用户正在输入，需要看到输入框）
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && uiState.messages.isNotEmpty()) {
            delay(150L) // 等待键盘动画完成
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    LaunchedEffect(currentSessionId) {
        if (currentSessionId.isNotEmpty()) {
            viewModel.loadMessages(currentSessionId)
        }
    }

    LaunchedEffect(Unit) {
        if (currentSessionId.isEmpty()) {
            scope.launch {
                try {
                    currentSessionId = viewModel.ensureSession()  // 不带 forceNew，优先恢复旧会话
                } catch (e: Exception) {
                    FileLogger.e("ChatScreen", "ensureSession failed", e)
                }
            }
        } else {
            scope.launch {
                try {
                    val result = viewModel.resumeSessionWithId(currentSessionId)
                    if (result.isFailure) {
                        FileLogger.w("ChatScreen", "resumeSession failed, will create new")
                        currentSessionId = viewModel.ensureSession()
                    }
                } catch (e: Exception) {
                    FileLogger.e("ChatScreen", "resumeSession failed", e)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GatewayEvent.ApprovalRequest -> pendingApproval.value = event
                is GatewayEvent.ClarifyRequest -> pendingClarify.value = event
                is GatewayEvent.SecretRequest -> pendingSecret.value = event
                is GatewayEvent.SudoRequest -> pendingSudo.value = event
                else -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSlashCommands()
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToSession.collect { sessionId ->
            currentSessionId = sessionId
        }
    }

    ApprovalDialog(
        request = pendingApproval.value,
        onApprove = { allowPermanent ->
            pendingApproval.value?.let {
                viewModel.respondApproval(it.id, approved = true, allowPermanent = allowPermanent)
                pendingApproval.value = null
            }
        },
        onReject = {
            pendingApproval.value?.let {
                viewModel.respondApproval(it.id, approved = false)
                pendingApproval.value = null
            }
        },
        onDismiss = {
            pendingApproval.value?.let {
                viewModel.respondApproval(it.id, approved = false)
                pendingApproval.value = null
            }
        }
    )

    ClarifyDialog(
        request = pendingClarify.value,
        onRespond = { response ->
            pendingClarify.value?.let {
                viewModel.respondClarify(it.id, response)
                pendingClarify.value = null
            }
        },
        onDismiss = {
            pendingClarify.value?.let {
                viewModel.respondClarify(it.id, "")
                pendingClarify.value = null
            }
        }
    )

    SecretDialog(
        request = pendingSecret.value,
        onRespond = { secret ->
            pendingSecret.value?.let {
                viewModel.respondSecret(it.id, secret)
                pendingSecret.value = null
            }
        },
        onDismiss = { pendingSecret.value = null }
    )

    SudoDialog(
        request = pendingSudo.value,
        onRespond = { password ->
            pendingSudo.value?.let {
                viewModel.respondSudo(it.id, password)
                pendingSudo.value = null
            }
        },
        onDismiss = {
            pendingSudo.value?.let {
                viewModel.respondSudo(it.id, "")
                pendingSudo.value = null
            }
        }
    )

    // 图片全屏预览弹窗（支持缩放、拖拽、双击放大）
    ImagePreviewDialog(
        imageUri = previewImageUri,
        onDismiss = { previewImageUri = null }
    )

    val title = if (currentSessionId.isEmpty()) "口袋星奈" else currentSessionId.take(8)

    // 拦截系统返回键：抽屉打开时关闭抽屉，否则不返回 connect 界面
    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < 2000) {
                onBack()
            } else {
                backPressedTime = currentTime
                android.widget.Toast.makeText(context, "再按一次返回主页", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.8f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SessionListScreen(
                        viewModel = hiltViewModel(),
                        onSessionSelected = { selectedId ->
                            scope.launch {
                                viewModel.resumeSessionWithId(selectedId)
                                currentSessionId = selectedId
                                drawerState.close()
                            }
                        },
                        onNewSession = { _ ->
                            scope.launch {
                                currentSessionId = viewModel.ensureSession(forceNew = true)
                                drawerState.close()
                            }
                        },
                        onReconfigure = {
                            scope.launch { drawerState.close() }
                            onReconfigure()
                        },
                        onNavigateToSettings = {
                            scope.launch { drawerState.close() }
                            onNavigateToSettings()
                        }
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!uiState.isSearchMode) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "菜单",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = title,
                        style = TextStyles.bodyMd,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isStreaming) {
                        Text(
                            text = uiState.statusText.ifBlank { "思考中…" },
                            style = TextStyles.bodySm,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        IconButton(onClick = { viewModel.stopGenerating() }) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "停止生成",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleSearchMode() }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "菜单",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    SeinaTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.sm),
                        placeholder = "搜索消息...",
                        singleLine = true
                    )
                    Text(
                        text = "只看我",
                        style = TextStyles.bodySm.copy(
                            color = if (uiState.searchFilterUserOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (uiState.searchFilterUserOnly) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        ),
                        modifier = Modifier
                            .clickable { viewModel.toggleSearchFilterUserOnly() }
                            .padding(horizontal = Spacing.xs)
                    )
                    IconButton(onClick = { viewModel.clearSearch() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭搜索",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            if (uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else if (uiState.error != null) {
                        Text(
                            text = uiState.error ?: "",
                            style = TextStyles.bodyMd,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (uiState.isSearchMode) {
                        Text(
                            text = "未找到匹配消息",
                            style = TextStyles.bodyMd,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    } else {
                        Text(
                            text = "开始聊天吧",
                            style = TextStyles.bodyMd,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            if (currentSessionId.isEmpty()) {
                                isRefreshing = false
                            } else {
                                isRefreshing = true
                                viewModel.loadMessages(currentSessionId, forceRefresh = true)
                            }
                        },
                        state = pullToRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = Spacing.md, end = 28.dp),
                            state = listState,
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(uiState.messages, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    showToolCalls = uiState.showToolCalls,
                                    showReasoning = uiState.showReasoning,
                                    showTimestamps = uiState.showTimestamps,
                                    hiddenToolNames = uiState.hiddenToolNames,
                                    onImageClick = { previewImageUri = it },
                                    onQuote = { viewModel.quoteMessage(it) },
                                    onResend = { viewModel.resendMessage(it) },
                                    onEdit = { viewModel.startEditingMessage(it) },
                                    onBranch = { viewModel.branchFromMessage(it.id) }
                                )
                            }
                        }
                    }

                    VerticalScrollbar(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = Spacing.xs)
                            .zIndex(1f)
                    )

                    if (!autoScrollEnabled && uiState.messages.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    if (uiState.messages.isNotEmpty()) {
                                        listState.animateScrollToItem(uiState.messages.size - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge {
                                            Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "滚到底部"
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.messages.isNotEmpty()) {
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        style = TextStyles.caption,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md)
                    )
                }
            }

            val quoted = uiState.quotedMessage
            if (quoted != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "引用 ${quoted.role}",
                            style = TextStyles.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = quoted.content.take(60),
                            style = TextStyles.bodySm,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "×",
                        style = TextStyles.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { viewModel.clearQuote() }
                    )
                }
            }

            if (editingMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MaterialTheme.shapes.medium)
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "编辑消息",
                        style = TextStyles.bodySm,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "取消",
                        style = TextStyles.bodySm.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        modifier = Modifier.clickable { viewModel.cancelEditing() }
                    )
                }
            }

            Composer(
                value = uiState.currentInput,
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                sendEnabled = uiState.canSend,
                slashCommands = slashCommands,
                selectedImages = uiState.selectedImages,
                onImagesSelected = viewModel::onImagesSelected,
                onRemoveImage = viewModel::removeSelectedImage,
                onImageClick = { previewImageUri = it.toString() },
                selectedFiles = uiState.selectedFiles,
                onFileSelected = viewModel::onFileSelected,
                onRemoveFile = viewModel::removeSelectedFile,
                editingMessage = editingMessage,
                onCancelEdit = viewModel::cancelEditing
            )
        }
    }
}
