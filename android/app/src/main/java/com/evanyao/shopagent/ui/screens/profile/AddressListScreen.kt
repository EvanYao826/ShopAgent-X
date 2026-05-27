package com.evanyao.shopagent.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF2D3436))
                }
            },
            actions = {
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, "新增地址", tint = Color(0xFFFF6B35))
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        if (uiState.isLoading && uiState.addressList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF6B35))
            }
        } else if (uiState.addressList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(48.dp), tint = Color(0xFFB2BEC3))
                    Spacer(Modifier.height(8.dp))
                    Text("暂无收货地址", color = Color(0xFF636E72))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))) {
                        Text("添加地址")
                    }
                }
            }
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
    val borderColor = if (item.isDefault) Color(0xFFFF6B35) else Color.Transparent

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(if (item.isDefault) 1.5.dp else 0.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.receiverName, fontWeight = FontWeight.SemiBold, color = Color(0xFF2D3436))
                Spacer(Modifier.width(12.dp))
                Text(item.phone, color = Color(0xFF636E72))
                if (item.isDefault) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "默认",
                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = Color(0xFFFF6B35),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF6B35).copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${item.province}${item.city}${item.district}${item.detail}",
                color = Color(0xFF2D3436),
                fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!item.isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Text("设为默认", color = Color(0xFFFF6B35), fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "编辑", tint = Color(0xFF636E72), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "删除", tint = Color(0xFFE17055), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}