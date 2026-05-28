package com.evanyao.shopagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AI 智能导购头像
 * 使用机器人图标，暖色背景
 */
@Composable
fun AiAvatar(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = "AI导购",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/**
 * 用户头像（根据性别自动选择颜色）
 * 男性：蓝色
 * 女性：粉色
 * 未知：主题色
 */
@Composable
fun UserAvatar(
    size: Dp = 36.dp,
    gender: Int? = null,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, iconColor) = when (gender) {
        1 -> Pair(Color(0xFFE3F2FD), Color(0xFF1976D2))  // 男性：蓝色
        2 -> Pair(Color(0xFFFCE4EC), Color(0xFFE91E63))  // 女性：粉色
        else -> Pair(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.primary
        )  // 未知：主题色
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "用户头像",
            tint = iconColor,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
