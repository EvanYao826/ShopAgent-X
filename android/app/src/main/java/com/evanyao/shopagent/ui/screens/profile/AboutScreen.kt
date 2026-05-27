package com.evanyao.shopagent.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val primaryColor = Color(0xFFFF6B35)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        TopAppBar(
            title = { Text("关于我们", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF2D3436))
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(primaryColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("S", fontWeight = FontWeight.Bold, color = primaryColor)
            }

            Spacer(Modifier.height(16.dp))

            Text("ShopAgent-X", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2D3436))
            Text("智能导购助手", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF636E72))

            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AboutRow("应用名称", "ShopAgent-X")
                HorizontalDivider(color = Color(0xFFF1F3F5))
                AboutRow("版本号", "1.0.0")
                HorizontalDivider(color = Color(0xFFF1F3F5))
                AboutRow("技术栈", "Kotlin + Spring Boot")
                HorizontalDivider(color = Color(0xFFF1F3F5))
                AboutRow("开发者", "EvanYao")
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "基于 AI 大模型的智能购物导购应用，为用户提供个性化的商品推荐和购物咨询体验。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF636E72),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF636E72))
        Text(value, color = Color(0xFF2D3436), fontWeight = FontWeight.Medium)
    }
}