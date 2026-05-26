package com.evanyao.shopagent.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evanyao.shopagent.ui.components.AppLoadingIndicator
import com.evanyao.shopagent.ui.components.EmptyState
import com.evanyao.shopagent.viewmodel.AddressItem
import com.evanyao.shopagent.viewmodel.AddressViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressListScreen(
    viewModel: AddressViewModel,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAddressList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        TopAppBar(
            title = { Text("收货地址", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = com.evanyao.shopagent.ui.theme.TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, "新增地址", tint = com.evanyao.shopagent.ui.theme.Primary)
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        if (uiState.isLoading && uiState.addressList.isEmpty()) {
            AppLoadingIndicator()
        } else if (uiState.addressList.isEmpty()) {
            EmptyState(
                icon = Icons.Default.LocationOn,
                message = "暂无收货地址",
                actionText = "添加地址",
                onAction = onAddClick
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.addressList, key = { it.id }) { item ->
                    AddressCard(
                        item = item,
                        onEdit = { onEditClick(item.id) },
                        onDelete = { viewModel.deleteAddress(item.id) },
                        onSetDefault = { viewModel.setDefault(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressCard(
    item: AddressItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    val borderColor = if (item.isDefault) com.evanyao.shopagent.ui.theme.Primary else Color.Transparent

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(if (item.isDefault) 1.5.dp else 0.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.receiverName, fontWeight = FontWeight.SemiBold, color = com.evanyao.shopagent.ui.theme.TextPrimary)
                Spacer(Modifier.width(12.dp))
                Text(item.phone, color = com.evanyao.shopagent.ui.theme.TextSecondary)
                if (item.isDefault) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "默认",
                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = com.evanyao.shopagent.ui.theme.Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(com.evanyao.shopagent.ui.theme.Primary.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${item.province}${item.city}${item.district}${item.detail}",
                color = com.evanyao.shopagent.ui.theme.TextPrimary,
                fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!item.isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Text("设为默认", color = com.evanyao.shopagent.ui.theme.Primary, fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "编辑", tint = com.evanyao.shopagent.ui.theme.TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "删除", tint = com.evanyao.shopagent.ui.theme.Error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}