package com.evanyao.shopagent.ui.screens.cart

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.evanyao.shopagent.ui.components.noFocusClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.evanyao.shopagent.data.model.CartItem
import com.evanyao.shopagent.data.model.ProductSku
import com.evanyao.shopagent.ui.components.buildImageUrl
import com.evanyao.shopagent.ui.components.AsyncImageWithPlaceholder
import com.evanyao.shopagent.ui.components.LoadingIndicator
import com.evanyao.shopagent.viewmodel.CartViewModel

// 主题配色（统一使用 MaterialTheme.colorScheme）
private val CartPrimary @Composable get() = MaterialTheme.colorScheme.primary
private val CartBg @Composable get() = MaterialTheme.colorScheme.background
private val CartPriceRed @Composable get() = MaterialTheme.colorScheme.error
private val CartTextPrimary @Composable get() = MaterialTheme.colorScheme.onSurface
private val CartTextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val CartDividerColor @Composable get() = MaterialTheme.colorScheme.outlineVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: CartViewModel,
    onProductClick: (Long) -> Unit,
    onCheckout: () -> Unit,
    onNavigateToProducts: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val editingSkuItem = uiState.editingSkuItem
    val listState = rememberLazyListState()

    // 错误提示
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // 规格选择弹窗
    if (editingSkuItem != null && uiState.editingSkus.isNotEmpty()) {
        CartSkuSelectionBottomSheet(
            cartItem = editingSkuItem,
            skus = uiState.editingSkus,
            onDismiss = { viewModel.cancelEditSku() },
            onConfirm = { newSkuId -> viewModel.confirmUpdateSku(newSkuId) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CartBg)
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部导航栏
        CartTopBar(itemCount = uiState.cartItems.size)

        when {
            uiState.isLoading -> {
                LoadingIndicator()
            }
            uiState.cartItems.isEmpty() -> {
                EmptyCartContent(onNavigateToProducts = onNavigateToProducts)
            }
            else -> {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        // 店铺分组（目前默认一个店铺）
                        item {
                            ShopSection(
                                shopName = "优选商城",
                                cartItems = uiState.cartItems,
                                selectedItems = uiState.selectedItems,
                                onProductClick = onProductClick,
                                onRemove = { cartItemId -> viewModel.removeFromCart(cartItemId) },
                                onToggleSelect = { viewModel.toggleItemSelection(it) },
                                onQuantityChange = { id, qty -> viewModel.updateQuantity(id, qty) },
                                onToggleShopSelect = { viewModel.toggleShopSelectAll() },
                                onSkuClick = { viewModel.startEditSku(it) }
                            )
                        }
                    }

                    // 滚动条
                    val layoutInfo = listState.layoutInfo
                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val avgItemHeight = if (visibleItems.isNotEmpty()) {
                        visibleItems.sumOf { it.size } / visibleItems.size
                    } else 120
                    val totalContentHeight = avgItemHeight * layoutInfo.totalItemsCount

                    if (totalContentHeight > viewportHeight) {
                        val thumbRatio = (viewportHeight.toFloat() / totalContentHeight).coerceIn(0.1f, 0.6f)
                        val trackHeightPx = viewportHeight.toFloat() - 16f
                        val thumbHeightPx = trackHeightPx * thumbRatio

                        val maxScroll = (totalContentHeight - viewportHeight).coerceAtLeast(1)
                        val firstItem = visibleItems.firstOrNull()
                        val scrolledPast = if (firstItem != null) {
                            firstItem.index * avgItemHeight + listState.firstVisibleItemScrollOffset
                        } else 0
                        val fraction = (scrolledPast.toFloat() / maxScroll).coerceIn(0f, 1f)
                        val thumbTravel = trackHeightPx - thumbHeightPx
                        val thumbOffsetDp = LocalDensity.current.run { (thumbTravel * fraction).toDp() }
                        val thumbHeightDp = LocalDensity.current.run { thumbHeightPx.toDp() }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(6.dp)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x22000000))
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(thumbHeightDp)
                                    .offset(y = thumbOffsetDp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }
        }

        // 底部结算栏
        BottomCheckoutBar(
            isAllSelected = uiState.isCheckedAll,
            totalPrice = uiState.totalPrice,
            selectedCount = uiState.selectedItemCount,
            onToggleSelectAll = { viewModel.toggleSelectAll() },
            onCheckout = {
                Toast.makeText(context, "暂不支持支付", Toast.LENGTH_SHORT).show()
            }
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}

@Composable
private fun CartTopBar(itemCount: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "购物车($itemCount)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CartTextPrimary
            )
        }
    }
}

@Composable
private fun EmptyCartContent(
    onNavigateToProducts: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = CartTextSecondary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "购物车是空的",
                fontSize = 16.sp,
                color = CartTextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CartPrimary
            ) {
                Text(
                    text = "去逛逛",
                    modifier = Modifier
                        .clickable(onClick = onNavigateToProducts)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopSection(
    shopName: String,
    cartItems: List<CartItem>,
    selectedItems: Set<Long>,
    onProductClick: (Long) -> Unit,
    onRemove: (Long) -> Unit,  // cartItemId
    onToggleSelect: (Long) -> Unit,
    onQuantityChange: (Long, Int) -> Unit,
    onToggleShopSelect: () -> Unit,
    onSkuClick: (CartItem) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 店铺头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = cartItems.all { selectedItems.contains(it.productId) },
                onCheckedChange = { onToggleShopSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = CartPrimary,
                    uncheckedColor = CartTextSecondary
                )
            )
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = null,
                tint = CartPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = shopName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = CartTextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = CartTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // 商品列表
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
        ) {
            cartItems.forEachIndexed { index, cartItem ->
                var isRevealed by remember { mutableStateOf(false) }
                val density = LocalDensity.current.density

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount < -20) {
                                        isRevealed = true
                                    } else if (dragAmount > 20) {
                                        isRevealed = false
                                    }
                                }
                            )
                        }
                ) {
                    // 背景删除按钮（始终在底层）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.error)
                                .clickable {
                                    onRemove(cartItem.id)
                                    isRevealed = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("删除", color = Color.White, fontSize = 14.sp)
                        }
                    }

                    // 前景内容（可滑动）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(if (isRevealed) (-80 * density).toInt() else 0, 0) }

                            .clickable(enabled = isRevealed) {
                                isRevealed = false
                            }
                    ) {
                        CartItemCard(
                            cartItem = cartItem,
                            isSelected = selectedItems.contains(cartItem.productId),
                            onProductClick = {
                                if (!isRevealed) onProductClick(cartItem.productId)
                            },
                            onToggleSelect = { onToggleSelect(cartItem.productId) },
                            onQuantityChange = { onQuantityChange(cartItem.productId, it) },
                            onSkuClick = { onSkuClick(cartItem) }
                        )
                    }
                }

                if (index < cartItems.size - 1) {
                    Divider(
                        modifier = Modifier.padding(start = 52.dp),
                        color = CartDividerColor,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun CartItemCard(
    cartItem: CartItem,
    isSelected: Boolean,
    onProductClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    onSkuClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .noFocusClickable(onClick = onProductClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 复选框
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() },
            colors = CheckboxDefaults.colors(
                checkedColor = CartPrimary,
                uncheckedColor = CartTextSecondary
            ),
            modifier = Modifier.padding(top = 20.dp)
        )

        // 商品图片
        AsyncImageWithPlaceholder(
            model = buildImageUrl(cartItem.product?.imageUrl),
            contentDescription = null,
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 商品信息
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 标题
            Text(
                text = cartItem.product?.title ?: "",
                fontSize = 14.sp,
                color = CartTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 规格标签（可点击更换）
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(onClick = onSkuClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cartItem.skuText,
                        fontSize = 11.sp,
                        color = CartTextSecondary
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = CartTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 价格和数量
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 价格
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥",
                        fontSize = 12.sp,
                        color = CartPriceRed,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%.2f", cartItem.sku?.price ?: cartItem.product?.basePrice ?: 0.0),
                        fontSize = 18.sp,
                        color = CartPriceRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 数量选择器
                QuantitySelector(
                    quantity = cartItem.quantity,
                    onDecrease = {
                        if (cartItem.quantity > 1) {
                            onQuantityChange(cartItem.quantity - 1)
                        }
                    },
                    onIncrease = { onQuantityChange(cartItem.quantity + 1) }
                )
            }
        }
    }
}

@Composable
private fun QuantitySelector(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
    ) {
        // 减号
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onDecrease),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (quantity > 1) CartTextPrimary else CartTextSecondary
            )
        }

        // 数量
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = quantity.toString(),
                fontSize = 14.sp,
                color = CartTextPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        // 加号
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onIncrease),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = CartTextPrimary
            )
        }
    }
}

@Composable
private fun BottomCheckoutBar(
    isAllSelected: Boolean,
    totalPrice: Double,
    selectedCount: Int,
    onToggleSelectAll: () -> Unit,
    onCheckout: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 全选
            Row(
                modifier = Modifier.clickable(onClick = onToggleSelectAll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isAllSelected,
                    onCheckedChange = { onToggleSelectAll() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = CartPrimary,
                        uncheckedColor = CartTextSecondary
                    )
                )
                Text(
                    text = "全选",
                    fontSize = 14.sp,
                    color = CartTextPrimary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 合计
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "合计: ",
                        fontSize = 14.sp,
                        color = CartTextPrimary
                    )
                    Text(
                        text = "¥",
                        fontSize = 12.sp,
                        color = CartPriceRed
                    )
                    Text(
                        text = String.format("%.2f", totalPrice),
                        fontSize = 18.sp,
                        color = CartPriceRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "不含运费",
                    fontSize = 11.sp,
                    color = CartTextSecondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 结算按钮
            Button(
                onClick = onCheckout,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CartPrimary,
                    disabledContainerColor = CartPrimary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = if (selectedCount > 0) "结算($selectedCount)" else "结算",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartSkuSelectionBottomSheet(
    cartItem: CartItem,
    skus: List<ProductSku>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    // 找到当前选中的 SKU 索引
    val currentSkuIndex = skus.indexOfFirst { it.id == cartItem.skuId }.coerceAtLeast(0)
    var selectedIndex by remember { mutableIntStateOf(currentSkuIndex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 标题
            Text(
                text = "更换规格",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // SKU 列表（固定高度，可滚动）
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(skus) { index, sku ->
                    SkuOptionItem(
                        propertiesText = sku.propertiesText.ifEmpty { "默认规格" },
                        price = sku.price.toDouble(),
                        stock = sku.stock,
                        isSelected = index == selectedIndex,
                        onClick = { selectedIndex = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 确认按钮（固定在底部）
            Button(
                onClick = {
                    val selectedSku = skus.getOrNull(selectedIndex)
                    if (selectedSku != null) {
                        onConfirm(selectedSku.id)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CartPrimary)
            ) {
                Text(
                    text = "确定",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SkuOptionItem(
    propertiesText: String,
    price: Double,
    stock: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        CartPrimary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isSelected) {
        CartPrimary
    } else {
        Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 0.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = propertiesText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "库存: $stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = CartTextSecondary
                )
            }
            Text(
                text = "¥${String.format("%.2f", price)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CartPriceRed
            )
        }
    }
}
