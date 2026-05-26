package com.evanyao.shopagent.ui.screens.profile

import androidx.compose.foundation.background
import com.evanyao.shopagent.ui.components.noFocusClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val primaryColor = com.evanyao.shopagent.ui.theme.Primary

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("确定", color = primaryColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消", color = com.evanyao.shopagent.ui.theme.TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.evanyao.shopagent.ui.theme.Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    "设置",
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 账号与安全
        SettingsSection(title = "账号与安全") {
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "修改密码",
                onClick = { }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 其他
        SettingsSection(title = "其他") {
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "退出登录",
                titleColor = com.evanyao.shopagent.ui.theme.Error,
                onClick = { showLogoutDialog = true }
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = com.evanyao.shopagent.ui.theme.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    titleColor: Color = com.evanyao.shopagent.ui.theme.TextPrimary,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noFocusClickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = titleColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = com.evanyao.shopagent.ui.theme.TextHint,
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 50.dp),
            color = com.evanyao.shopagent.ui.theme.SurfaceVariant
        )
    }
}
