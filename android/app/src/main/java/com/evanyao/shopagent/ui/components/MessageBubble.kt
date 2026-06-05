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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanyao.shopagent.data.model.Message
import com.evanyao.shopagent.data.model.ConfirmCard
import com.evanyao.shopagent.data.model.Product

/** 消息气泡组件，支持长按复制、点赞/踩反馈、确认卡片 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isStreaming: Boolean = false,
    userGender: Int? = null,
    onProductClick: ((Long) -> Unit)? = null,
    onFeedback: ((Long, Int) -> Unit)? = null,
    onConfirmAction: ((String) -> Unit)? = null,
    onAddToCart: ((List<Long>) -> Unit)? = null
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
            AiAvatar(size = 36.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = if (message.cartSelection != null) 340.dp else 280.dp)
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

            // 确认卡片（购物车删除/修改确认）
            if (!isUser && message.messageType == "confirm_card" && message.confirmCard != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ConfirmCardView(
                    confirmCard = message.confirmCard!!,
                    onAction = { actionType ->
                        onConfirmAction?.invoke(actionType)
                    }
                )
            }

            // 购物车选择卡片（批量加购 / 查看购物车）
            if (!isUser && message.cartSelection != null) {
                Spacer(modifier = Modifier.height(8.dp))
                if (message.messageType == "cart_list") {
                    // 查看购物车：纵向排列，类似确认删除卡片样式
                    CartProductList(products = message.cartSelection!!.items)
                } else {
                    // 批量加购：带复选框
                    SelectableProductCardList(
                        products = message.cartSelection!!.items,
                        onAddToCart = { productIds ->
                            onAddToCart?.invoke(productIds)
                        }
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar(size = 36.dp, gender = userGender)
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

/** 确认卡片组件：显示商品信息 + 确认/取消按钮 */
@Composable
fun ConfirmCardView(
    confirmCard: ConfirmCard,
    onAction: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 批量商品列表（纵向排列）
            val products = confirmCard.products
            if (!products.isNullOrEmpty()) {
                products.forEachIndexed { index, product ->
                    ConfirmProductRow(product)
                    if (index < products.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                // 单个商品信息（兼容旧格式）
                confirmCard.product?.let { product ->
                    ConfirmProductRow(product)
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 确认消息
            Text(
                text = confirmCard.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                confirmCard.buttons.forEach { button ->
                    val isConfirm = button.type == "confirm"
                    val answered = confirmCard.answered
                    OutlinedButton(
                        onClick = { if (!answered) onAction(button.type) },
                        enabled = !answered,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (answered) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            } else if (isConfirm) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = if (isConfirm && !answered) {
                            ButtonDefaults.outlinedButtonBorder(enabled = true)
                        } else {
                            null
                        }
                    ) {
                        Text(
                            text = button.label,
                            fontWeight = if (isConfirm && !answered) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/** 确认卡片中的单个商品行 */
@Composable
private fun ConfirmProductRow(product: Product) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val imageUrl = buildImageUrl(product.imageUrl)
        if (imageUrl != null) {
            AsyncImageWithPlaceholder(
                model = imageUrl,
                contentDescription = product.title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = product.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "¥${product.basePrice}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                product.brand?.let { brand ->
                    Text(
                        text = brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 购物车列表卡片：纵向展示商品，带序号，样式同确认删除卡片 */
@Composable
fun CartProductList(products: List<Product>) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            products.forEachIndexed { index, product ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 序号
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp)
                    )

                    // 商品图片
                    val imageUrl = buildImageUrl(product.imageUrl)
                    if (imageUrl != null) {
                        AsyncImageWithPlaceholder(
                            model = imageUrl,
                            contentDescription = product.title,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // 商品信息
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "¥${product.basePrice}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            product.brand?.let { brand ->
                                Text(
                                    text = brand,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (index < products.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
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
