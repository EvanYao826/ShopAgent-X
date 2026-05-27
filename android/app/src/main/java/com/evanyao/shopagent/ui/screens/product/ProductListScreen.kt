package com.evanyao.shopagent.ui.screens.product

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.evanyao.shopagent.data.model.Product
import com.evanyao.shopagent.ui.components.buildImageUrl
import com.evanyao.shopagent.ui.components.noFocusClickable
import com.evanyao.shopagent.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductViewModel,
    onProductClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        viewModel.getFavoriteList()
    }

    // 上拉加载更多
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                val totalItems = gridState.layoutInfo.totalItemsCount
                if (lastIndex != null && lastIndex >= totalItems - 4) {
                    viewModel.loadMore()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索商品...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )

        // 分类标签
        if (uiState.categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryChip(
                        name = "全部",
                        selected = uiState.selectedCategoryId == null,
                        onClick = { viewModel.selectCategory(null) }
                    )
                }
                items(uiState.categories) { category ->
                    CategoryChip(
                        name = category.name,
                        selected = uiState.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) }
                    )
                }
            }
        }

        // 商品列表
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.products.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                uiState.errorMessage != null -> "加载失败"
                                uiState.searchQuery.isNotBlank() -> "没有找到相关商品"
                                else -> "暂无商品"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.errorMessage != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.weight(1f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.products) { product ->
                            ProductGridCard(
                                product = product,
                                isFavorite = uiState.favoriteProductIds?.contains(product.id) == true,
                                onClick = { onProductClick(product.id) },
                                onToggleFavorite = { productId ->
                                    viewModel.toggleFavorite(productId)
                                }
                            )
                        }
                        // 底部加载指示器
                        if (uiState.isLoadingMore) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }

                    // 滚动条
                    val canScroll = gridState.canScrollForward || gridState.canScrollBackward
                    if (canScroll) {
                        val density = LocalDensity.current
                        val layoutInfo = gridState.layoutInfo
                        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                        val thumbHeightPx = 40f
                        val trackHeightPx = viewportHeight.toFloat() - thumbHeightPx

                        val visibleItems = layoutInfo.visibleItemsInfo
                        val avgItemHeight = if (visibleItems.isNotEmpty()) {
                            visibleItems.sumOf { it.size.height } / visibleItems.size
                        } else 200
                        val totalContentHeight = avgItemHeight * (layoutInfo.totalItemsCount / 2 + 1)
                        val maxScroll = (totalContentHeight - viewportHeight).coerceAtLeast(1)

                        val firstItem = visibleItems.firstOrNull()
                        val scrolledPast = if (firstItem != null) {
                            firstItem.index / 2 * avgItemHeight + gridState.firstVisibleItemScrollOffset
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
                }
            }
        }
    }

    // 错误提示
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.noFocusClickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProductGridCard(
    product: Product,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .noFocusClickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = buildImageUrl(product.imageUrl),
                    contentDescription = product.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
                // 收藏按钮
                FavoriteButton(
                    isFavorite = isFavorite,
                    productId = product.id,
                    onClick = onToggleFavorite
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

@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    productId: Long,
    onClick: (Long) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.IconButton(
            onClick = { onClick(productId) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color(0x80000000))
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Favorite,
                contentDescription = if (isFavorite) "已收藏" else "收藏",
                tint = if (isFavorite) Color(0xFFFF6B35) else Color(0x80FFFFFF)
            )
        }
    }
}
