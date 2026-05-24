package com.evanyao.shopagent.ui.screens.cart

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import coil.compose.AsyncImage
import com.evanyao.shopagent.data.model.CartItem
import com.evanyao.shopagent.data.model.ProductSku
import com.evanyao.shopagent.ui.components.buildImageUrl
import com.evanyao.shopagent.viewmodel.CartViewModel

// 淘宝风格配色
private val TaobaoOrange = Color(0xFFFF5000)
private val TaobaoBg = Color(0xFFF5F5F5)
private val PriceRed = Color(0xFFFF4444)
private val TextPrimary = Color(0xFF333333)
private val TextSecondary = Color(0xFF999999)
private val DividerColor = Color(0xFFE5E5E5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: CartViewModel,
    onProductClick: (Long) -> Unit,
    onCheckout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val editingSkuItem = uiState.editingSkuItem

    // 规格选择弹窗
    if (editingSkuItem != null && uiState.editingSkus.isNotEmpty()) {
        CartSkuSelectionBottomSheet(
            cartItem = editingSkuItem,
            skus = uiState.editingSkus,
            onDismiss = { viewModel.cancelEditSku() },
            onConfirm = { newSkuId -> viewModel.confirmUpdateSku(newSkuId) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TaobaoBg)
    ) {
        // 顶部导航栏
        CartTopBar(itemCount = uiState.cartItems.size)

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TaobaoOrange)
                }
            }
            uiState.cartItems.isEmpty() -> {
                EmptyCartContent()
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
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
}

@Composable
private fun CartTopBar(itemCount: Int) {
    Surface(
        color = Color.White,
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
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun EmptyCartContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFFCCCCCC)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "购物车是空的",
                fontSize = 16.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TaobaoOrange
            ) {
                Text(
                    text = "去逛逛",
                    modifier = Modifier
                        .clickable { }
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
                .background(Color.White, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = cartItems.all { selectedItems.contains(it.productId) },
                onCheckedChange = { onToggleShopSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = TaobaoOrange,
                    uncheckedColor = Color(0xFFCCCCCC)
                )
            )
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = null,
                tint = TaobaoOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = shopName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // 商品列表
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
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
                                .background(Color(0xFFFF4444))
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
                        color = DividerColor,
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
            .background(Color.White)
            .clickable(onClick = onProductClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 复选框
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() },
            colors = CheckboxDefaults.colors(
                checkedColor = TaobaoOrange,
                uncheckedColor = Color(0xFFCCCCCC)
            ),
            modifier = Modifier.padding(top = 20.dp)
        )

        // 商品图片
        AsyncImage(
            model = buildImageUrl(cartItem.product?.imageUrl),
            contentDescription = null,
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5))
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
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 规格标签（可点击更换）
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFFF5F5F5),
                modifier = Modifier.clickable(onClick = onSkuClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cartItem.skuText,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextSecondary
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
                        color = PriceRed,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%.2f", cartItem.sku?.price ?: cartItem.product?.basePrice ?: 0.0),
                        fontSize = 18.sp,
                        color = PriceRed,
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
            .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
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
                tint = if (quantity > 1) TextPrimary else TextSecondary
            )
        }

        // 数量
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(28.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = quantity.toString(),
                fontSize = 14.sp,
                color = TextPrimary,
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
                tint = TextPrimary
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
                        checkedColor = TaobaoOrange,
                        uncheckedColor = Color(0xFFCCCCCC)
                    )
                )
                Text(
                    text = "全选",
                    fontSize = 14.sp,
                    color = TextPrimary
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
                        color = TextPrimary
                    )
                    Text(
                        text = "¥",
                        fontSize = 12.sp,
                        color = PriceRed
                    )
                    Text(
                        text = String.format("%.2f", totalPrice),
                        fontSize = 18.sp,
                        color = PriceRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "不含运费",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 结算按钮
            Button(
                onClick = onCheckout,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TaobaoOrange,
                    disabledContainerColor = Color(0xFFFFB088)
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
                colors = ButtonDefaults.buttonColors(containerColor = TaobaoOrange)
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
        TaobaoOrange.copy(alpha = 0.1f)
    } else {
        Color(0xFFF5F5F5)
    }
    val borderColor = if (isSelected) {
        TaobaoOrange
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
                    color = TextSecondary
                )
            }
            Text(
                text = "¥${String.format("%.2f", price)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PriceRed
            )
        }
    }
}
