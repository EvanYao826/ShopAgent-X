package com.evanyao.shopagent.ui.screens.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import com.evanyao.shopagent.data.model.CreateOrderItem
import com.evanyao.shopagent.data.model.Order
import com.evanyao.shopagent.data.model.User
import com.evanyao.shopagent.ui.components.AsyncImageWithPlaceholder
import com.evanyao.shopagent.ui.components.LoadingIndicator
import com.evanyao.shopagent.ui.components.buildImageUrl
import com.evanyao.shopagent.viewmodel.AddressItem
import com.evanyao.shopagent.viewmodel.OrderViewModel
import java.math.BigDecimal

data class CheckoutItem(
    val productId: Long,
    val skuId: Long?,
    val title: String,
    val image: String?,
    val skuText: String,
    val price: BigDecimal,
    val quantity: Int
) {
    val totalPrice: BigDecimal get() = price.multiply(BigDecimal(quantity))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    orderViewModel: OrderViewModel,
    checkoutItems: List<CheckoutItem>,
    defaultAddress: AddressItem?,
    user: User?,
    onBack: () -> Unit,
    onAddressClick: () -> Unit,
    onOrderCreated: (Long) -> Unit
) {
    val orderState by orderViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var remark by remember { mutableStateOf("") }
    var receiverName by remember { mutableStateOf(defaultAddress?.receiverName ?: user?.username ?: "") }
    var receiverPhone by remember { mutableStateOf(defaultAddress?.phone ?: user?.phone ?: "") }

    // 地址切换时更新收货人信息
    LaunchedEffect(defaultAddress) {
        if (defaultAddress != null) {
            receiverName = defaultAddress.receiverName
            receiverPhone = defaultAddress.phone
        }
    }

    LaunchedEffect(orderState.createSuccess) {
        if (orderState.createSuccess && orderState.currentOrder != null) {
            onOrderCreated(orderState.currentOrder!!.id)
            orderViewModel.clearCreateSuccess()
        }
    }

    LaunchedEffect(orderState.errorMessage) {
        orderState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            orderViewModel.clearError()
        }
    }

    val totalPrice = checkoutItems.fold(java.math.BigDecimal.ZERO) { acc, item -> acc.add(item.totalPrice) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SnackbarHost(hostState = snackbarHostState)
        TopAppBar(
            title = { Text("确认订单", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (checkoutItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无商品信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // 收货地址
                item {
                    AddressCard(address = defaultAddress, onClick = onAddressClick)
                }

                // 收货人信息
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "收货人信息",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = receiverName,
                                onValueChange = { receiverName = it },
                                label = { Text("收货人姓名") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = receiverPhone,
                                onValueChange = { receiverPhone = it },
                                label = { Text("手机号") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
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

                items(checkoutItems) { item ->
                    CheckoutItemCard(item = item)
                }

                // 备注
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        OutlinedTextField(
                            value = remark,
                            onValueChange = { remark = it },
                            label = { Text("订单备注") },
                            placeholder = { Text("选填，有什么要交代的") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                }

                // 价格明细
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    PriceSummary(
                        itemsTotal = totalPrice,
                        freight = java.math.BigDecimal.ZERO
                    )
                }
            }

            if (orderState.isLoading) {
                LoadingIndicator()
            }
        }

        // 底部提交栏
        SubmitOrderBar(
            totalPrice = totalPrice,
            enabled = defaultAddress != null && checkoutItems.isNotEmpty() && receiverName.isNotBlank() && receiverPhone.isNotBlank(),
            onSubmit = {
                val addressId = defaultAddress?.id ?: return@SubmitOrderBar
                val items = checkoutItems.map {
                    CreateOrderItem(it.productId, it.skuId, it.quantity)
                }
                orderViewModel.createOrder(
                    addressId, items, remark.ifBlank { null },
                    receiverName.ifBlank { null }, receiverPhone.ifBlank { null }
                )
            }
        )
    }
}

@Composable
private fun AddressCard(address: AddressItem?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (address != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text(
                            text = address.receiverName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = address.phone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${address.province}${address.city}${address.district}${address.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Text(
                    text = "请选择收货地址",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CheckoutItemCard(item: CheckoutItem) {
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
                model = buildImageUrl(item.image),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.skuText != "默认") {
                    Text(
                        text = item.skuText,
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
private fun PriceSummary(itemsTotal: java.math.BigDecimal, freight: java.math.BigDecimal) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("商品金额", style = MaterialTheme.typography.bodyMedium)
                Text("¥${String.format("%.2f", itemsTotal)}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("运费", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (freight > java.math.BigDecimal.ZERO) "¥${String.format("%.2f", freight)}" else "免运费",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text("合计: ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "¥${String.format("%.2f", itemsTotal.add(freight))}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SubmitOrderBar(totalPrice: java.math.BigDecimal, enabled: Boolean, onSubmit: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("实付金额", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "¥${String.format("%.2f", totalPrice)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = onSubmit,
                enabled = enabled,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 32.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("提交订单", fontSize = 16.sp)
            }
        }
    }
}
