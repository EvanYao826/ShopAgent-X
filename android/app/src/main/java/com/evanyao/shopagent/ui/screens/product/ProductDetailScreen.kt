package com.evanyao.shopagent.ui.screens.product

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.evanyao.shopagent.ui.components.buildImageUrl
import com.evanyao.shopagent.viewmodel.ProductViewModel

@Composable
fun ProductDetailScreen(
    viewModel: ProductViewModel,
    productId: Long,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val detailState = uiState.productDetail

    LaunchedEffect(productId) {
        viewModel.loadProductDetail(productId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (detailState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (detailState.errorMessage != null) {
            Text(
                text = detailState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (detailState.product != null) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                    // 商品图片
                    item {
                        ProductImageSection(imageUrl = detailState.product.imageUrl)
                    }

                    // 商品基本信息
                    item {
                        ProductInfoSection(
                            title = detailState.product.title,
                            brand = detailState.product.brand,
                            price = detailState.product.basePrice.toString(),
                            rating = detailState.product.rating?.toString(),
                            salesCount = detailState.product.salesCount
                        )
                    }

                    // 商品描述
                    if (!detailState.product.description.isNullOrBlank()) {
                        item {
                            DescriptionSection(description = detailState.product.description)
                        }
                    }

                    // SKU 选择
                    if (detailState.skus.isNotEmpty()) {
                        item {
                            SkuSection(skus = detailState.skus)
                        }
                    }

                    // 用户评价
                    if (detailState.reviews.isNotEmpty()) {
                        item {
                            ReviewsSection(reviews = detailState.reviews)
                        }
                    }

                    // 常见问题
                    if (detailState.faqs.isNotEmpty()) {
                        item {
                            FaqsSection(faqs = detailState.faqs)
                        }
                    }

                    // 底部间距
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }

                // 滚动条
            val canScroll = listState.canScrollForward || listState.canScrollBackward
            if (canScroll) {
                val density = LocalDensity.current
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val thumbHeightPx = 40f
                val trackHeightPx = viewportHeight.toFloat() - thumbHeightPx

                val visibleItems = layoutInfo.visibleItemsInfo
                val avgItemHeight = if (visibleItems.isNotEmpty()) {
                    visibleItems.sumOf { it.size } / visibleItems.size
                } else 100
                val totalContentHeight = avgItemHeight * layoutInfo.totalItemsCount
                val maxScroll = (totalContentHeight - viewportHeight).coerceAtLeast(1)

                val firstItem = visibleItems.firstOrNull()
                val scrolledPast = if (firstItem != null) {
                    firstItem.index * avgItemHeight + listState.firstVisibleItemScrollOffset
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

            // 底部操作栏
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { /* 加入购物车 */ },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text("加入购物车")
                    }
                    Button(
                        onClick = { /* 立即购买 */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("立即购买")
                    }
                }
            }

            // 透明标题栏覆盖层
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Text(
                    text = "商品详情",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ProductImageSection(imageUrl: String?) {
    val context = LocalContext.current
    val finalUrl = buildImageUrl(imageUrl)
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(finalUrl)
            .crossfade(true)
            .listener(
                onError = { _, result ->
                    Log.e("Coil", "Image load failed: $finalUrl", result.throwable)
                },
                onSuccess = { _, _ ->
                    Log.d("Coil", "Image loaded: $finalUrl")
                }
            )
            .build(),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun ProductInfoSection(
    title: String,
    brand: String?,
    price: String,
    rating: String?,
    salesCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // 品牌
        if (!brand.isNullOrBlank()) {
            Text(
                text = brand,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 标题
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 价格
        Text(
            text = "¥$price",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 评分和销量
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (rating != null) {
                Text(
                    text = "★ $rating",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${salesCount}人付款",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DescriptionSection(description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "商品描述",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SkuSection(skus: List<Map<String, Any>>) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "规格选择",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(skus) { index, sku ->
                SkuChip(
                    sku = sku,
                    isSelected = index == selectedIndex,
                    onClick = { selectedIndex = index }
                )
            }
        }
    }
}

@Composable
private fun SkuChip(
    sku: Map<String, Any>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val properties = sku["properties"] as? Map<*, *>
    val price = (sku["price"] as? Number)?.toString() ?: ""
    val propertiesText = properties?.entries?.joinToString(" ") { "${it.key}: ${it.value}" } ?: ""

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Card(
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(if (isSelected) 2.dp else 0.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (propertiesText.isNotBlank()) {
                Text(
                    text = propertiesText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "¥$price",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun ReviewsSection(reviews: List<Map<String, Any>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "用户评价 (${reviews.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        reviews.forEach { review ->
            ReviewItem(review = review)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReviewItem(review: Map<String, Any>) {
    val nickname = review["nickname"] as? String ?: "匿名用户"
    val rating = ((review["rating"] as? Number)?.toInt() ?: 5).coerceIn(1, 5)
    val content = review["content"] as? String ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "★".repeat(rating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FaqsSection(faqs: List<Map<String, Any>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "常见问题",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        faqs.forEach { faq ->
            FaqItem(faq = faq)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FaqItem(faq: Map<String, Any>) {
    val question = faq["question"] as? String ?: ""
    val answer = faq["answer"] as? String ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Q: $question",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A: $answer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
