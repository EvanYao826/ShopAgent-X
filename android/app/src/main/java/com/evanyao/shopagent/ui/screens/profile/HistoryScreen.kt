package com.evanyao.shopagent.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.evanyao.shopagent.ui.components.noFocusClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanyao.shopagent.ui.components.AsyncImageWithPlaceholder
import com.evanyao.shopagent.ui.components.LoadingIndicator
import com.evanyao.shopagent.ui.components.EmptyState
import com.evanyao.shopagent.ui.components.ErrorState
import com.evanyao.shopagent.viewmodel.HistoryGroup
import com.evanyao.shopagent.viewmodel.HistoryItem
import com.evanyao.shopagent.viewmodel.HistoryViewModel
import com.evanyao.shopagent.ui.components.buildImageUrl
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onProductClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    "浏览历史",
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

        when {
            uiState.isLoading -> {
                LoadingIndicator()
            }
            uiState.errorMessage != null -> {
                ErrorState(
                    message = uiState.errorMessage!!,
                    onRetry = { viewModel.loadHistory() }
                )
            }
            uiState.groups.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "暂无浏览历史"
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = { viewModel.loadHistory() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        for (group in uiState.groups) {
                            item(key = "header_${group.label}") {
                                GroupHeader(group)
                            }
                            items(items = group.items, key = { it.id }) {
                                HistoryItemRow(item = it, onClick = { onProductClick(it.productId) })
                            }
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
}


@Composable
private fun GroupHeader(group: HistoryGroup) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = group.label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryItemRow(item: HistoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.productPrice,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.browseTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
