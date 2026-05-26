package com.evanyao.shopagent.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evanyao.shopagent.ui.components.noFocusClickable
import com.evanyao.shopagent.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    phone: String? = null,
    onSettingsClick: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onHistoryClick: () -> Unit,
    onAddressClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryColor = com.evanyao.shopagent.ui.theme.Primary

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    val user = uiState.user

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.evanyao.shopagent.ui.theme.Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    "我的",
                    fontWeight = FontWeight.SemiBold
                )
            },
            actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = com.evanyao.shopagent.ui.theme.TextSecondary
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // User info card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .noFocusClickable(onClick = onEditProfileClick)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.username ?: "未设置昵称",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = com.evanyao.shopagent.ui.theme.TextPrimary
                        )
                        Text(
                            text = phone ?: user?.phone ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.evanyao.shopagent.ui.theme.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = com.evanyao.shopagent.ui.theme.TextHint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // User profile info
                if (user != null && hasProfileInfo(user)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "个人资料",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = com.evanyao.shopagent.ui.theme.TextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gender & Age
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            user.gender?.let { gender ->
                                val genderText = when (gender) {
                                    1 -> "男"
                                    2 -> "女"
                                    else -> "不想说"
                                }
                                ProfileTag(text = genderText, color = primaryColor)
                            }
                            user.ageRange?.let { age ->
                                ProfileTag(text = "${age}岁", color = com.evanyao.shopagent.ui.theme.TagPurple)
                            }
                            user.skinType?.let { skin ->
                                ProfileTag(text = skin, color = com.evanyao.shopagent.ui.theme.TagGreen)
                            }
                        }

                        // Preference tags
                        if (!user.preferenceTags.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                user.preferenceTags.forEach { tag ->
                                    ProfileTag(text = tag, color = com.evanyao.shopagent.ui.theme.TextSecondary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Menu items
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    ProfileMenuItem(
                        icon = Icons.Default.Favorite,
                        title = "我的收藏",
                        onClick = onFavoritesClick
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.History,
                        title = "浏览历史",
                        onClick = onHistoryClick
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.LocationOn,
                        title = "收货地址",
                        onClick = onAddressClick
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.Info,
                        title = "关于我们",
                        onClick = onAboutClick
                    )
                }
            }

            // Loading overlay
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = primaryColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun ProfileTag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun hasProfileInfo(user: com.evanyao.shopagent.data.model.User): Boolean {
    return user.gender != null ||
            user.ageRange != null ||
            user.skinType != null ||
            !user.preferenceTags.isNullOrEmpty()
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noFocusClickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = com.evanyao.shopagent.ui.theme.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = com.evanyao.shopagent.ui.theme.TextPrimary,
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
            modifier = Modifier.padding(start = 52.dp),
            color = com.evanyao.shopagent.ui.theme.SurfaceVariant
        )
    }
}
