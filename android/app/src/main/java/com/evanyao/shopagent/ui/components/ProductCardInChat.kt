package com.evanyao.shopagent.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.evanyao.shopagent.data.model.Product
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val BASE_URL = "http://10.0.2.2:8888"

/** 编码图片 URL，处理中文字符 */
fun encodeImageUrl(imageUrl: String?): String? {
    if (imageUrl.isNullOrBlank()) return null
    return try {
        // 简单的路径编码：对每个路径段分别编码
        imageUrl.split("/").joinToString("/") { segment ->
            // 检查是否包含中文字符
            if (segment.contains(Regex("[一-龥]"))) {
                URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
            } else {
                segment
            }
        }
    } catch (e: Exception) {
        Log.e("ImageURL", "Failed to encode URL: $imageUrl", e)
        imageUrl
    }
}

/** 构建完整的图片 URL */
fun buildImageUrl(imageUrl: String?): String? {
    if (imageUrl.isNullOrBlank()) return null
    val url = when {
        imageUrl.startsWith("http") -> imageUrl.replace("localhost:8080", "10.0.2.2:8888")
                                               .replace("localhost:8888", "10.0.2.2:8888")
                                               .replace("localhost", "10.0.2.2:8888")
        imageUrl.startsWith("/product-images/") -> "$BASE_URL$imageUrl"
        else -> "$BASE_URL/product-images/$imageUrl"
    }
    val encoded = encodeImageUrl(url)
    Log.d("ImageURL", "Original: $imageUrl -> Final: $encoded")
    return encoded
}

/** 商品卡片列表，横向滚动展示 */
@Composable
fun ProductCardList(
    products: List<Product>,
    onProductClick: ((Long) -> Unit)? = null
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(products) { product ->
            ProductCardInChat(
                product = product,
                onClick = { onProductClick?.invoke(product.id) }
            )
        }
    }
}

/** 可勾选的商品卡片列表，用于批量加购 */
@Composable
fun SelectableProductCardList(
    products: List<Product>,
    onAddToCart: (List<Long>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var added by remember { mutableStateOf(false) }

    Column {
        if (added) {
            // 添加成功提示
            Text(
                text = "已成功加入购物车",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            // 横向滚动的商品卡片（带复选框）
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products) { product ->
                    SelectableProductCard(
                        product = product,
                        isSelected = selectedIds.contains(product.id),
                        onToggle = {
                            selectedIds = if (selectedIds.contains(product.id)) {
                                selectedIds - product.id
                            } else {
                                selectedIds + product.id
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 确认按钮
            Button(
                onClick = {
                    onAddToCart(selectedIds.toList())
                    added = true
                    selectedIds = emptySet()
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("加入购物车（${selectedIds.size}件）")
            }
        }
    }
}

/** 带复选框的商品卡片 */
@Composable
private fun SelectableProductCard(
    product: Product,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column {
            // 复选框覆盖在图片右上角
            Box {
                val imageUrl = buildImageUrl(product.imageUrl)
                AsyncImageWithPlaceholder(
                    model = imageUrl,
                    contentDescription = product.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${product.basePrice}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** 对话中的商品卡片组件 */
@Composable
fun ProductCardInChat(
    product: Product,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // 商品图片
            val imageUrl = buildImageUrl(product.imageUrl)
            AsyncImageWithPlaceholder(
                model = imageUrl,
                contentDescription = product.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                // 商品标题
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 价格和数量
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "¥${product.basePrice}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (product.quantity > 1) {
                        Text(
                            text = "x${product.quantity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 评分和销量
                if (product.rating != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "★ ${product.rating}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${product.salesCount}人付款",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
