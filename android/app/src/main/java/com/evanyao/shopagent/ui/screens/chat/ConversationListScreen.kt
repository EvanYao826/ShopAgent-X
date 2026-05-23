package com.evanyao.shopagent.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evanyao.shopagent.data.model.Conversation
import com.evanyao.shopagent.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onConversationClick: (Conversation) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Conversation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("会话列表") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.createConversation() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建会话"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "暂无会话",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击右上角 + 创建新会话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(uiState.conversations) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        onClick = {
                            viewModel.selectConversation(conversation)
                            onConversationClick(conversation)
                        },
                        onDelete = { showDeleteDialog = conversation }
                    )
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

    // 错误提示
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            viewModel.clearError()
        }
    }
}

@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = conversation.title ?: "未命名会话",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = conversation.createTime ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
