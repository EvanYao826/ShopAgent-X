package com.evanyao.shopagent.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evanyao.shopagent.ui.components.noFocusClickable
import com.evanyao.shopagent.ui.components.AsyncImageWithPlaceholder
import com.evanyao.shopagent.ui.components.LoadingIndicator
import com.evanyao.shopagent.ui.components.EmptyState
import com.evanyao.shopagent.viewmodel.FavoriteViewModel
import com.evanyao.shopagent.viewmodel.FavoriteItem
import com.evanyao.shopagent.ui.components.buildImageUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoriteViewModel,
    onBack: () -> Unit,
    onProductClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "我的收藏",
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.loadFavorites() },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading && uiState.favorites.isEmpty() -> {
                    LoadingIndicator()
                }
                uiState.favorites.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.FavoriteBorder,
                        title = "暂无收藏"
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState
                    ) {
                        items(uiState.favorites) { favorite ->
                            FavoriteItemCard(
                                item = favorite,
                                onClick = { onProductClick(favorite.productId) }
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
                        val density = LocalDensity.current
                        // 滑块大小按比例：可视区域占总内容的比例
                        val thumbRatio = (viewportHeight.toFloat() / totalContentHeight).coerceIn(0.1f, 0.6f)
                        val trackHeightPx = viewportHeight.toFloat() - 16f // 减去上下 padding
                        val thumbHeightPx = trackHeightPx * thumbRatio

                        val maxScroll = (totalContentHeight - viewportHeight).coerceAtLeast(1)
                        val firstItem = visibleItems.firstOrNull()
                        val scrolledPast = if (firstItem != null) {
                            firstItem.index * avgItemHeight + listState.firstVisibleItemScrollOffset
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

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}

@Composable
private fun FavoriteItemCard(item: FavoriteItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .noFocusClickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImageWithPlaceholder(
                model = buildImageUrl(item.productImage),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.productPrice,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "收藏时间: ${item.createTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
