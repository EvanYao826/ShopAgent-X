package com.evanyao.shopagent.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onComplete: (gender: Int, ageRange: String, skinType: String, tags: List<String>) -> Unit,
    onSkip: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var selectedGender by remember { mutableIntStateOf(0) }
    var selectedAgeRange by remember { mutableStateOf("") }
    var selectedSkinType by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }

    val primaryColor = Color(0xFFFF6B35)
    val backgroundLight = Color(0xFFFFF8F5)

    val genderOptions = listOf(
        1 to "男",
        2 to "女",
        0 to "不想说"
    )

    val ageOptions = listOf("18-24", "25-30", "31-40", "40+")

    val skinTypeOptions = listOf("干性", "油性", "混合性", "中性", "敏感肌")

    val tagCategories = mapOf(
        "风格" to listOf("简约", "时尚", "运动", "复古", "甜美", "商务"),
        "偏好" to listOf("平价优选", "大牌正品", "新品尝鲜", "爆款推荐", "小众好物"),
        "关注" to listOf("美妆护肤", "服饰鞋包", "数码家电", "食品生鲜", "家居日用")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // 顶部：进度条 + 跳过
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = (currentStep + 1) / 4f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = primaryColor,
                    trackColor = Color(0xFFE8ECEF)
                )
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text("跳过", color = Color(0xFF636E72))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (currentStep) {
                    0 -> {
                        // 步骤1：性别
                        Text(
                            text = "你是？",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF2D3436)
                        )
                        Text(
                            text = "帮助我们为你推荐更合适的内容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF636E72),
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        genderOptions.forEach { (value, label) ->
                            val isSelected = selectedGender == value
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) primaryColor.copy(alpha = 0.1f) else Color.White)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) primaryColor else Color(0xFFE8ECEF),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedGender = value },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) primaryColor else Color(0xFF2D3436),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    1 -> {
                        // 步骤2：年龄段
                        Text(
                            text = "你的年龄段",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF2D3436)
                        )
                        Text(
                            text = "不同年龄有不同的潮流趋势",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF636E72),
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        ageOptions.forEach { age ->
                            val isSelected = selectedAgeRange == age
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) primaryColor.copy(alpha = 0.1f) else Color.White)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) primaryColor else Color(0xFFE8ECEF),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedAgeRange = age },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${age}岁",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) primaryColor else Color(0xFF2D3436),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    2 -> {
                        // 步骤3：肤质
                        Text(
                            text = "你的肤质是？",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF2D3436)
                        )
                        Text(
                            text = "帮助我们推荐适合你的护肤美妆产品",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF636E72),
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        skinTypeOptions.forEach { skinType ->
                            val isSelected = selectedSkinType == skinType
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) primaryColor.copy(alpha = 0.1f) else Color.White)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) primaryColor else Color(0xFFE8ECEF),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedSkinType = skinType },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = skinType,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) primaryColor else Color(0xFF2D3436),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    3 -> {
                        // 步骤4：偏好标签
                        Text(
                            text = "你的购物偏好",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF2D3436)
                        )
                        Text(
                            text = "选择你感兴趣的标签（可多选）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF636E72),
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        tagCategories.forEach { (category, tags) ->
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF2D3436),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            // 简单的标签网格布局
                            val rows = tags.chunked(3)
                            rows.forEach { rowTags ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowTags.forEach { tag ->
                                        val isSelected = tag in selectedTags
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedTags = if (isSelected) {
                                                    selectedTags - tag
                                                } else {
                                                    selectedTags + tag
                                                }
                                            },
                                            label = { Text(tag) },
                                            leadingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.height(16.dp)
                                                    )
                                                }
                                            } else null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = primaryColor,
                                                selectedLabelColor = Color.White,
                                                selectedLeadingIconColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    }
                                    // 填充空位
                                    repeat(3 - rowTags.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // 底部按钮
            Button(
                onClick = {
                    when (currentStep) {
                        0, 1, 2 -> currentStep++
                        3 -> onComplete(selectedGender, selectedAgeRange, selectedSkinType, selectedTags.toList())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text(
                    text = if (currentStep == 3) "完成" else "下一步",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                )
            }
        }
    }
}
