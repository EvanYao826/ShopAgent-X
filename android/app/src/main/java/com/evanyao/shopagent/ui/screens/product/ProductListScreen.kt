package com.evanyao.shopagent.ui.screens.product

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evanyao.shopagent.data.model.Product
import com.evanyao.shopagent.ui.components.buildImageUrl
import com.evanyao.shopagent.ui.components.noFocusClickable
import com.evanyao.shopagent.ui.components.AsyncImageWithPlaceholder
import com.evanyao.shopagent.ui.components.LoadingIndicator
import com.evanyao.shopagent.ui.components.EmptyState
import com.evanyao.shopagent.ui.components.ErrorState
import com.evanyao.shopagent.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductViewModel,
    onProductClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = uiState.scrollPosition,
        initialFirstVisibleItemScrollOffset = uiState.scrollOffset
    )
    val coroutineScope = rememberCoroutineScope()

    // 保存滚动位置的函数
    val savePosition = {
        viewModel.saveScrollPosition(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
    }

    // 记录上一次的分类和搜索词，避免返回时重复滚动到顶部
    var lastCategoryId by remember { mutableStateOf(uiState.selectedCategoryId) }
    var lastSearchQuery by remember { mutableStateOf(uiState.searchQuery) }

    // 当分类或搜索词真正变化时，滚动到顶部
    LaunchedEffect(uiState.selectedCategoryId, uiState.searchQuery) {
        val categoryChanged = uiState.selectedCategoryId != lastCategoryId
        val searchChanged = uiState.searchQuery != lastSearchQuery
        if (categoryChanged || searchChanged) {
            lastCategoryId = uiState.selectedCategoryId
            lastSearchQuery = uiState.searchQuery
            snapshotFlow { uiState.isLoading }
                .first { !it }
            gridState.scrollToItem(0)
        }
    }

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
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除")
                    }
                }
            },
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

        // 排序选项
        if (uiState.searchQuery.isBlank()) {
            SortBar(
                sortBy = uiState.sortBy,
                sortOrder = uiState.sortOrder,
                onSortChange = { viewModel.setSortBy(it) }
            )
        }

        // 商品列表
        when {
            uiState.isLoading -> {
                LoadingIndicator()
            }
            uiState.products.isEmpty() -> {
                if (uiState.errorMessage != null) {
                    ErrorState(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.refresh() }
                    )
                } else {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = if (uiState.searchQuery.isNotBlank()) "没有找到相关商品" else "暂无商品"
                    )
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.weight(1f)
                ) {
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
                                onClick = {
                                    savePosition()
                                    onProductClick(product.id)
                                },
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

                    // 回到顶部按钮
                    val showScrollToTop = gridState.firstVisibleItemIndex > 0
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToTop,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    gridState.animateScrollToItem(0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "回到顶部")
                        }
                    }

                    // 滚动条
                    val layoutInfo = gridState.layoutInfo
                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val avgItemHeight = if (visibleItems.isNotEmpty()) {
                        visibleItems.sumOf { it.size.height } / visibleItems.size
                    } else 200
                    val totalContentHeight = avgItemHeight * (layoutInfo.totalItemsCount / 2 + 1)

                    if (totalContentHeight > viewportHeight) {
                        val density = LocalDensity.current
                        val thumbRatio = (viewportHeight.toFloat() / totalContentHeight).coerceIn(0.1f, 0.6f)
                        val trackHeightPx = viewportHeight.toFloat() - 16f
                        val thumbHeightPx = trackHeightPx * thumbRatio

                        val maxScroll = (totalContentHeight - viewportHeight).coerceAtLeast(1)
                        val firstItem = visibleItems.firstOrNull()
                        val scrolledPast = if (firstItem != null) {
                            firstItem.index / 2 * avgItemHeight + gridState.firstVisibleItemScrollOffset
                        } else 0
                        val fraction = (scrolledPast.toFloat() / maxScroll).coerceIn(0f, 1f)
                        val thumbTravel = trackHeightPx - thumbHeightPx
                        val thumbOffsetDp = density.run { (thumbTravel * fraction).toDp() }
                        val thumbHeightDp = density.run { thumbHeightPx.toDp() }

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
    }

    // 错误提示
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.BottomCenter)
    )
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
                AsyncImageWithPlaceholder(
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
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Favorite,
                contentDescription = if (isFavorite) "已收藏" else "收藏",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SortBar(
    sortBy: String,
    sortOrder: String,
    onSortChange: (String) -> Unit
) {
    val sortOptions = listOf(
        "sales" to "销量",
        "price" to "价格",
        "rating" to "评分",
        "newest" to "最新"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        sortOptions.forEach { (key, label) ->
            val isSelected = sortBy == key
            val arrow = if (isSelected) {
                if (sortOrder == "desc") "↓" else "↑"
            } else ""

            Text(
                text = "$label$arrow",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.noFocusClickable { onSortChange(key) }
            )
        }
    }
}
