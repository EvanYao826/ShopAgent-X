package com.evanyao.shopagent.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.History
import com.evanyao.shopagent.ui.components.AppLoadingIndicator
import com.evanyao.shopagent.ui.components.EmptyState
import com.evanyao.shopagent.ui.components.ErrorState
import com.evanyao.shopagent.ui.components.noFocusClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
                        tint = com.evanyao.shopagent.ui.theme.TextPrimary
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )

        when {
            uiState.isLoading -> {
                AppLoadingIndicator()
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
                    message = "暂无浏览历史"
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
            }
        }
    }
}


@Composable
private fun GroupHeader(group: HistoryGroup) {
    Surface(
        color = com.evanyao.shopagent.ui.theme.SurfaceElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = group.label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = com.evanyao.shopagent.ui.theme.TextSecondary
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
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
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
                    color = com.evanyao.shopagent.ui.theme.TextPrimary,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.productPrice,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = com.evanyao.shopagent.ui.theme.Primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.browseTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color= com.evanyao.shopagent.ui.theme.TextSecondary
                )
            }
        }
    }
}