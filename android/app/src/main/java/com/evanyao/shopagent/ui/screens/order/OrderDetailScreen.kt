package com.evanyao.shopagent.ui.screens.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanyao.shopagent.data.model.Order
import com.evanyao.shopagent.data.model.OrderItem
import com.evanyao.shopagent.ui.components.AsyncImageWithPlaceholder
import com.evanyao.shopagent.ui.components.LoadingIndicator
import com.evanyao.shopagent.ui.components.buildImageUrl
import com.evanyao.shopagent.viewmodel.OrderViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    viewModel: OrderViewModel,
    orderId: Long,
    onBack: () -> Unit,
    onPaymentSuccess: (List<Long>) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRemoveCartDialog by remember { mutableStateOf(false) }
    var paymentDetected by remember { mutableStateOf(false) }
    var prevStatus by remember { mutableStateOf<Int?>(null) }
    val order = uiState.currentOrder

    // 检测支付成功：仅当状态从待付款(0)变为待发货(1)时标记一次
    LaunchedEffect(order?.status) {
        val current = order?.status
        if (prevStatus == Order.STATUS_PENDING_PAY && current == Order.STATUS_PENDING_DELIVERY) {
            paymentDetected = true
        }
        if (prevStatus != null) {
            prevStatus = current
        } else if (current != null) {
            // 首次加载：记录初始状态，不触发任何事件
            prevStatus = current
        }
    }

    // 支付成功且商品不为空时弹窗（仅一次）
    LaunchedEffect(paymentDetected) {
        if (paymentDetected && order != null) {
            val productIds = order.items.map { it.productId }
            if (productIds.isNotEmpty()) {
                showRemoveCartDialog = true
            }
            paymentDetected = false
        }
    }

    LaunchedEffect(orderId) {
        viewModel.loadOrderDetail(orderId)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("订单详情", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading && uiState.currentOrder == null -> {
                    LoadingIndicator()
                }
                uiState.currentOrder == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("订单不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    val currentOrder = uiState.currentOrder!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // 状态栏
                        item {
                            OrderStatusBar(
                                status = currentOrder.status,
                                deadlineMillis = uiState.currentPaymentDeadline,
                                onTimeout = { viewModel.cancelOrder(currentOrder.id) }
                            )
                        }

                        // 收货地址
                        if (currentOrder.receiverName != null) {
                            item {
                                DetailAddressCard(
                                    name = currentOrder.receiverName,
                                    phone = currentOrder.receiverPhone ?: "",
                                    address = currentOrder.receiverAddress ?: ""
                                )
                            }
                        }

                        // 商品列表
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "商品信息",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(currentOrder.items) { item ->
                            DetailItemCard(item = item)
                        }

                        // 价格明细
                        item {
                            PriceDetailCard(order = currentOrder)
                        }

                        // 订单信息
                        item {
                            OrderInfoCard(order = currentOrder)
                        }
                    }
                }
            }
        }

        // 底部操作按钮
        uiState.currentOrder?.let { order ->
            OrderActionBar(
                order = order,
                onPay = { viewModel.payOrder(order.id) },
                onCancel = { viewModel.cancelOrder(order.id) },
                onConfirmReceive = { viewModel.confirmReceive(order.id) }
            )
        }
    }

    // 支付成功后弹出：是否从购物车删除
    if (showRemoveCartDialog && order != null) {
        AlertDialog(
            onDismissRequest = { showRemoveCartDialog = false },
            title = { Text("移除购物车商品") },
            text = { Text("订单已支付成功，是否将已购买的商品从购物车中删除？") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveCartDialog = false
                    onPaymentSuccess(order.items.map { it.productId })
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveCartDialog = false }) {
                    Text("保留")
                }
            }
        )
    }
}

@Composable
private fun OrderStatusBar(status: Int, deadlineMillis: Long, onTimeout: () -> Unit) {
    val (icon, text, color) = when (status) {
        Order.STATUS_PENDING_PAY -> Triple("⏳", "等待付款", MaterialTheme.colorScheme.error)
        Order.STATUS_PENDING_DELIVERY -> Triple("📦", "等待发货", MaterialTheme.colorScheme.tertiary)
        Order.STATUS_PENDING_RECEIVE -> Triple("🚚", "待收货", MaterialTheme.colorScheme.primary)
        Order.STATUS_COMPLETED -> Triple("✅", "交易完成", MaterialTheme.colorScheme.primary)
        Order.STATUS_CANCELLED -> Triple("❌", "已取消", MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple("❓", "未知状态", MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            // 待付款状态显示倒计时
            if (status == Order.STATUS_PENDING_PAY && deadlineMillis > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                PaymentCountdown(deadlineMillis = deadlineMillis, onTimeout = onTimeout)
            }
        }
    }
}

@Composable
private fun PaymentCountdown(deadlineMillis: Long, onTimeout: () -> Unit) {
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var timeoutFired by remember { mutableStateOf(false) }

    LaunchedEffect(deadlineMillis) {
        timeoutFired = false
        while (true) {
            val remain = ((deadlineMillis - System.currentTimeMillis()) / 1000).toInt()
            if (remain <= 0) {
                remainingSeconds = 0
                if (!timeoutFired) {
                    timeoutFired = true
                    onTimeout()
                }
                break
            }
            remainingSeconds = remain
            delay(1000)
        }
    }

    if (remainingSeconds > 0) {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        Text(
            text = "请在 ${String.format("%02d:%02d", minutes, seconds)} 内完成支付，超时将自动取消",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun DetailAddressCard(name: String, phone: String, address: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailItemCard(item: OrderItem) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImageWithPlaceholder(
                model = buildImageUrl(item.productImage),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.skuProperties?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "¥${String.format("%.2f", item.price)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "x${item.quantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceDetailCard(order: Order) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("价格明细", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("商品金额", style = MaterialTheme.typography.bodyMedium)
                Text("¥${String.format("%.2f", order.totalAmount)}", style = MaterialTheme.typography.bodyMedium)
            }
            if (order.freightAmount > java.math.BigDecimal.ZERO) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("运费", style = MaterialTheme.typography.bodyMedium)
                    Text("¥${String.format("%.2f", order.freightAmount)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("实付: ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "¥${String.format("%.2f", order.payAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun OrderInfoCard(order: Order) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("订单信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("订单号", order.orderNo)
            order.createTime?.let { InfoRow("创建时间", it) }
            order.payTime?.let { InfoRow("支付时间", it) }
            order.deliveryTime?.let { InfoRow("发货时间", it) }
            order.receiveTime?.let { InfoRow("收货时间", it) }
            order.remark?.let { InfoRow("备注", it) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OrderActionBar(
    order: Order,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onConfirmReceive: () -> Unit
) {
    if (order.status == Order.STATUS_COMPLETED || order.status == Order.STATUS_CANCELLED) return

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (order.status) {
                Order.STATUS_PENDING_PAY -> {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) {
                        Text("取消订单")
                    }
                    Button(onClick = onPay) {
                        Text("立即支付")
                    }
                }
                Order.STATUS_PENDING_RECEIVE -> {
                    Button(onClick = onConfirmReceive) {
                        Text("确认收货")
                    }
                }
            }
        }
    }
}
