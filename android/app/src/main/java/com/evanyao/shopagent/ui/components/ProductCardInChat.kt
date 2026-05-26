package com.evanyao.shopagent.ui.components

import android.util.Log
import com.evanyao.shopagent.ui.components.noFocusClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

private const val BASE_URL = "http://10.0.2.2:8080"

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

fun buildImageUrl(imageUrl: String?): String? {
    if (imageUrl.isNullOrBlank()) return null
    val url = when {
        imageUrl.startsWith("http") -> imageUrl.replace("localhost", "10.0.2.2")
        imageUrl.startsWith("/product-images/") -> "$BASE_URL$imageUrl"
        else -> "$BASE_URL/product-images/$imageUrl"
    }
    val encoded = encodeImageUrl(url)
    Log.d("ImageURL", "Original: $imageUrl -> Final: $encoded")
    return encoded
}

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

@Composable
fun ProductCardInChat(
    product: Product,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .noFocusClickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // 商品图片
            val context = LocalContext.current
            val imageUrl = buildImageUrl(product.imageUrl)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .listener(
                        onError = { _, result ->
                            Log.e("Coil", "Image load failed: $imageUrl", result.throwable)
                        },
                        onSuccess = { _, _ ->
                            Log.d("Coil", "Image loaded: $imageUrl")
                        }
                    )
                    .build(),
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

                // 价格
                Text(
                    text = "¥${product.basePrice}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

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
