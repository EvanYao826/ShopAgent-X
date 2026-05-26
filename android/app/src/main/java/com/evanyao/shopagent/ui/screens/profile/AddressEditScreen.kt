package com.evanyao.shopagent.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evanyao.shopagent.viewmodel.AddressViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressEditScreen(
    viewModel: AddressViewModel,
    editId: Long? = null,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var receiverName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    val primaryColor = com.evanyao.shopagent.ui.theme.Primary

    LaunchedEffect(uiState.addressList) {
        if (editId != null && !initialized) {
            val item = uiState.addressList.find { it.id == editId }
            if (item != null) {
                receiverName = item.receiverName
                phone = item.phone
                province = item.province
                city = item.city
                district = item.district
                detail = item.detail
                isDefault = item.isDefault
                initialized = true
            }
        }
    }

    LaunchedEffect(uiState.operationSuccess) {
        if (uiState.operationSuccess) {
            viewModel.clearOperationSuccess()
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.evanyao.shopagent.ui.theme.Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        TopAppBar(
            title = { Text(if (editId != null) "编辑地址" else "新增地址", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = com.evanyao.shopagent.ui.theme.TextPrimary)
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FormSection("收货人") {
                OutlinedTextField(
                    value = receiverName,
                    onValueChange = { receiverName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("请输入收货人姓名") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            FormSection("手机号") {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("请输入手机号") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FormSection("省") {
                        OutlinedTextField(
                            value = province,
                            onValueChange = { province = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("省") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    FormSection("市") {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("市") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    FormSection("区/县") {
                        OutlinedTextField(
                            value = district,
                            onValueChange = { district = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("区/县") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            FormSection("详细地址") {
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("请输入详细地址") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .clip(RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("设为默认地址", modifier = Modifier.weight(1f), color = com.evanyao.shopagent.ui.theme.TextPrimary)
                Switch(checked = isDefault, onCheckedChange = { isDefault = it }, colors = SwitchDefaults.colors(checkedTrackColor = primaryColor))
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (editId != null) {
                        viewModel.updateAddress(editId, receiverName, phone, province, city, district, detail, isDefault)
                    } else {
                        viewModel.addAddress(receiverName, phone, province, city, district, detail, isDefault)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                enabled = receiverName.isNotBlank() && phone.isNotBlank() && detail.isNotBlank()
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }

            uiState.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FormSection(label: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = com.evanyao.shopagent.ui.theme.TextPrimary, fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))
        Spacer(Modifier.height(8.dp))
        content()
    }
}
