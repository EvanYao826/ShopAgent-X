package com.evanyao.shopagent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecommendSection(
    recommendations: List<String>,
    onRecommendClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "猜你想问",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 使用 FlowRow 布局推荐问题
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 每行两个推荐
            for (i in recommendations.indices step 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RecommendChip(
                        text = recommendations[i],
                        onClick = { onRecommendClick(recommendations[i]) },
                        modifier = Modifier.weight(1f)
                    )
                    if (i + 1 < recommendations.size) {
                        RecommendChip(
                            text = recommendations[i + 1],
                            onClick = { onRecommendClick(recommendations[i + 1]) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}
