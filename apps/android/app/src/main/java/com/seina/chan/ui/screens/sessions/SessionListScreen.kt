package com.seina.chan.ui.screens.sessions

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seina.chan.data.model.Session
import com.seina.chan.ui.components.GlobalEventViewModel
import com.seina.chan.data.remote.ConnectionState
import com.seina.chan.ui.components.ConnectionStatus
import com.seina.chan.ui.components.ConnectionStatusBar
import com.seina.chan.ui.components.SeinaButton
import com.seina.chan.ui.components.VerticalScrollbar
import com.seina.chan.ui.components.SeinaButtonVariant
import com.seina.chan.ui.components.SeinaTextField
import androidx.compose.material3.MaterialTheme
import com.seina.chan.ui.theme.Spacing
import com.seina.chan.ui.theme.TextStyles
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onSessionSelected: (String) -> Unit,
    onNewSession: (String) -> Unit,
    onReconfigure: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val sessions by viewModel.filteredSessions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val globalEventViewModel = hiltViewModel<GlobalEventViewModel>()
    val stableConnectionState by globalEventViewModel.stableConnectionState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var menuSession by remember { mutableStateOf<Session?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var renameSession by remember { mutableStateOf<Session?>(null) }
    var renameText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    // 初始加载
    LaunchedEffect(Unit) {
        viewModel.loadSessions()
    }

    // 监听导航事件
    LaunchedEffect(Unit) {
        viewModel.navigateToSession.collect { sessionId ->
            selectedSessionId = sessionId
            onSessionSelected(sessionId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.newSessionCreated.collect { sessionId ->
            onNewSession(sessionId)
        }
    }

    // 检测列表滚动到底部，触发加载更多
    LaunchedEffect(listState, hasMore, isLoadingMore, isLoading, isRefreshing) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3
        }
            .distinctUntilChanged()
            .collect { reachedEnd ->
                if (reachedEnd && hasMore && !isLoadingMore && !isLoading && !isRefreshing) {
                    viewModel.loadMore()
                }
            }
    }

    val connectionStatus = when (stableConnectionState) {
        is ConnectionState.Idle -> ConnectionStatus.Disconnected("未连接")
        is ConnectionState.Connecting -> ConnectionStatus.Connecting
        is ConnectionState.Open -> ConnectionStatus.Connected
        is ConnectionState.Closed -> ConnectionStatus.Disconnected("未连接")
        is ConnectionState.Error -> ConnectionStatus.Disconnected((stableConnectionState as ConnectionState.Error).reason)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.sm)
            .padding(bottom = Spacing.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "会话",
                style = TextStyles.displaySm
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Spacing.xs)
            )
            SeinaTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = "搜索会话...",
                modifier = Modifier.weight(1f)
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        ConnectionStatusBar(
            status = connectionStatus,
            modifier = Modifier.padding(horizontal = Spacing.sm)
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        SeinaButton(
            text = "+ 新会话",
            onClick = { viewModel.createNewSession() },
            variant = SeinaButtonVariant.Primary,
            compact = true,
            modifier = Modifier.padding(horizontal = Spacing.sm)
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            val isSearchActive = searchQuery.isNotEmpty()

            if (sessions.isEmpty() && !isLoading && !isRefreshing) {
                // 空状态提示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            error != null -> "加载失败: $error"
                            isSearchActive -> "未找到匹配会话"
                            else -> "暂无历史会话"
                        },
                        style = TextStyles.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (sessions.isEmpty() && isLoading) {
                // 初始加载中
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(sessions.size) { index ->
                            val session = sessions[index]
                            Box {
                                SessionListItem(
                                    session = session,
                                    isSelected = session.id == selectedSessionId,
                                    isLast = index == sessions.lastIndex,
                                    onClick = { viewModel.selectSession(session.id) },
                                    onLongClick = {
                                        menuSession = session
                                        showMenu = true
                                    },
                                    onUndo = { viewModel.undoSession(session.id) },
                                    onCompress = { viewModel.compressSession(session.id) }
                                )
                                DropdownMenu(
                                    expanded = showMenu && menuSession?.id == session.id,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("重命名") },
                                        onClick = {
                                            renameSession = menuSession
                                            renameText = menuSession?.title ?: ""
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {
                                            menuSession?.let { viewModel.deleteSession(it.id) }
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // 底部加载指示器或加载更多按钮
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else if (hasMore && !isSearchActive) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SeinaButton(
                                        text = "加载更多",
                                        onClick = { viewModel.loadMore() },
                                        variant = SeinaButtonVariant.Secondary,
                                        compact = true
                                    )
                                }
                            }
                        }
                    }

                    if (!isSearchActive) {
                        VerticalScrollbar(
                            state = listState,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .zIndex(1f)
                        )
                    }
                }
            }
        }

        if (renameSession != null) {
            AlertDialog(
                onDismissRequest = { renameSession = null },
                title = { Text("重命名会话") },
                text = {
                    SeinaTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        placeholder = "输入新名称",
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        renameSession?.let { viewModel.renameSession(it.id, renameText) }
                        renameSession = null
                    }) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameSession = null }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
