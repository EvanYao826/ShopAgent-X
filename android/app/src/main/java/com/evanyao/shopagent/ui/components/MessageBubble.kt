package com.evanyao.shopagent.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanyao.shopagent.data.model.Message
import com.evanyao.shopagent.ui.theme.Primary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isStreaming: Boolean = false,
    userGender: Int? = null,
    onProductClick: ((Long) -> Unit)? = null,
    onFeedback: ((Long, Int) -> Unit)? = null
) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignment = if (isUser) {
        Arrangement.End
    } else {
        Arrangement.Start
    }

    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = alignment
    ) {
        if (!isUser) {
            // AI 助手头像
            AiAvatar()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // 消息气泡（长按弹出菜单）
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    )
                    .padding(12.dp)
            ) {
                if (isStreaming) {
                    StreamingText(
                        text = message.content,
                        color = textColor
                    )
                } else {
                    Text(
                        text = message.content,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // 反馈状态显示
            if (!isUser && message.feedbackType != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (message.feedbackType == 1) "已点赞" else "已踩",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // 商品卡片
            if (!isUser && !message.productCards.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ProductCardList(
                    products = message.productCards,
                    onProductClick = onProductClick
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像
            UserAvatar(gender = userGender)
        }
    }

    // 长按菜单
    if (showMenu && !isUser) {
        MessageContextMenu(
            message = message,
            context = context,
            onDismiss = { showMenu = false },
            onFeedback = onFeedback
        )
    }
}

@Composable
fun AiAvatar() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = "AI助手",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun UserAvatar(gender: Int?) {
    val (icon, bgColor) = when (gender) {
        1 -> Icons.Default.Man to MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        2 -> Icons.Default.Woman to MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        else -> Icons.Default.Face to MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }
    val iconTint = when (gender) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "用户",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MessageContextMenu(
    message: Message,
    context: Context,
    onDismiss: () -> Unit,
    onFeedback: ((Long, Int) -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("消息操作") },
        text = {
            Column {
                // 复制文本
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("复制文本")
                }

                // 点赞
                if (message.feedbackType != 1) {
                    TextButton(
                        onClick = {
                            onFeedback?.invoke(message.id, 1)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("点赞")
                    }
                }

                // 踩
                if (message.feedbackType != 2) {
                    TextButton(
                        onClick = {
                            onFeedback?.invoke(message.id, 2)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("踩")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun StreamingText(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    Row {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyLarge
        )
        // 闪烁光标
        Text(
            text = "│",
            color = color.copy(alpha = cursorAlpha),
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 16.sp
        )
    }
}
