package com.evanyao.shopagent.ui.screens.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evanyao.shopagent.data.model.Conversation
import com.evanyao.shopagent.data.model.Message
import com.evanyao.shopagent.ui.components.MessageBubble
import com.evanyao.shopagent.ui.components.RecommendSection
import com.evanyao.shopagent.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onProductClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf<Conversation?>(null) }
    var showRenameDialog by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    val recommendations = uiState.recommendations

    // 自动滚动到底部（包括流式输出时）
    LaunchedEffect(uiState.messages.size, uiState.isSending, uiState.streamingContent) {
        if (uiState.messages.isNotEmpty() || uiState.isStreaming) {
            val targetIndex = uiState.messages.size
            if (targetIndex > 0) {
                listState.animateScrollToItem(targetIndex - 1)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "会话列表",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(
                        onClick = {
                            viewModel.clearCurrentConversation()
                            scope.launch { drawerState.close() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "新对话"
                        )
                    }
                }

                HorizontalDivider()

                val drawerListState = rememberLazyListState()

                if (uiState.conversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无会话\n点击右上角气泡新建",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = drawerListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.conversations) { conversation ->
                                ConversationDrawerItem(
                                    conversation = conversation,
                                    isSelected = uiState.currentConversation?.id == conversation.id,
                                    onClick = {
                                        viewModel.selectConversation(conversation)
                                        scope.launch { drawerState.close() }
                                    },
                                    onPin = {
                                        viewModel.pinConversation(conversation.id, !conversation.isPinned)
                                    },
                                    onDelete = { showDeleteDialog = conversation },
                                    onRename = {
                                        renameText = conversation.title ?: ""
                                        showRenameDialog = conversation
                                    }
                                )
                            }
                        }

                        val canScroll = drawerListState.canScrollForward || drawerListState.canScrollBackward

                        if (canScroll) {
                            val density = LocalDensity.current
                            val layoutInfo = drawerListState.layoutInfo
                            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                            val thumbHeightPx = 40f
                            val trackHeightPx = viewportHeight.toFloat() - thumbHeightPx

                            val visibleItems = layoutInfo.visibleItemsInfo
                            val avgItemHeight = if (visibleItems.isNotEmpty()) {
                                visibleItems.sumOf { it.size } / visibleItems.size
                            } else 60
                            val totalContentHeight = avgItemHeight * uiState.conversations.size
                            val maxScroll = (totalContentHeight - viewportHeight).coerceAtLeast(1)

                            val firstItem = visibleItems.firstOrNull()
                            val scrolledPast = if (firstItem != null) {
                                firstItem.index * avgItemHeight + drawerListState.firstVisibleItemScrollOffset
                            } else 0
                            val fraction = (scrolledPast.toFloat() / maxScroll).coerceIn(0f, 1f)
                            val thumbOffsetDp = density.run { (trackHeightPx * fraction).toDp() }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0x33FDD835))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(40.dp)
                                        .offset(y = thumbOffsetDp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFFDD835))
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.currentConversation?.title ?: "智能导购",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "会话列表"
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )

            // 消息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // 推荐问题
                if (uiState.messages.isEmpty() && uiState.currentConversation == null) {
                    item {
                        RecommendSection(
                            recommendations = recommendations,
                            onRecommendClick = { question ->
                                viewModel.createConversationAndSendMessage(question)
                            }
                        )
                    }
                }

                // 历史消息
                items(uiState.messages) { message ->
                    MessageBubble(
                        message = message,
                        onProductClick = onProductClick,
                        onFeedback = { messageId, feedbackType ->
                            viewModel.submitFeedback(messageId, feedbackType)
                        }
                    )
                }

                // 流式输出中的消息（实时更新）
                if (uiState.isStreaming && uiState.streamingContent.isNotBlank()) {
                    item {
                        val streamingMessage = Message(
                            id = -1,
                            conversationId = uiState.currentConversation?.id ?: 0,
                            role = "assistant",
                            content = uiState.streamingContent
                        )
                        MessageBubble(
                            message = streamingMessage,
                            isStreaming = true,
                            onProductClick = onProductClick
                        )
                    }
                }

                // 三点跳动加载动画
                if (uiState.isSending && !uiState.isStreaming) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            // 输入区域
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp),
                        placeholder = { Text("输入你的问题...") },
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (uiState.isStreaming) {
                        // 流式输出中显示停止按钮
                        IconButton(
                            onClick = { viewModel.cancelStream() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "停止",
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    } else {
                        // 发送按钮
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val text = inputText.trim()
                                    inputText = ""
                                    if (uiState.currentConversation == null) {
                                        viewModel.createConversationAndSendMessage(text)
                                    } else {
                                        viewModel.sendMessage(text)
                                    }
                                }
                            },
                            enabled = inputText.isNotBlank() && !uiState.isSending
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "发送",
                                tint = if (inputText.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 删除确认对话框
    showDeleteDialog?.let { conversation ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除会话") },
            text = { Text("确定要删除「${conversation.title ?: "未命名会话"}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(conversation.id)
                        showDeleteDialog = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 重命名对话框
    showRenameDialog?.let { conversation ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = { Text("输入新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameConversation(conversation.id, renameText.trim())
                        }
                        showRenameDialog = null
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 错误提示
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            viewModel.clearError()
        }
    }
}

/**
 * 三点跳动加载动画
 */
@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // AI 头像占位
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        )
        Spacer(modifier = Modifier.width(8.dp))

        // 三个跳动的点
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0..2) {
                val delay = i * 150
                val offsetY by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = -8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(300, delayMillis = delay, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot$i"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { translationY = offsetY }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
fun ConversationDrawerItem(
    conversation: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (conversation.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已置顶",
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = conversation.title ?: "未命名会话",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (conversation.createTime != null) {
                    Text(
                        text = conversation.createTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (conversation.isPinned) "取消置顶" else "置顶") },
                        onClick = {
                            showMenu = false
                            onPin()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}
