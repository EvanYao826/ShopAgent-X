package com.evanyao.shopagent.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HistoryItem(
    val id: Long,
    val productId: Long,
    val productName: String,
    val productImage: String?,
    val productPrice: String,
    val browseTime: LocalDateTime,
    val source: String?
)

data class HistoryGroup(
    val label: String,
    val items: List<HistoryItem>
)

data class HistoryUiState(
    val isLoading: Boolean = false,
    val groups: List<HistoryGroup> = emptyList(),
    val errorMessage: String? = null
)

class HistoryViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    // 后端时区（服务器 JVM 时区，通常是 Asia/Shanghai）
    private val serverZone = ZoneId.of("Asia/Shanghai")
    private val deviceZone = ZoneId.systemDefault()

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = productRepository.getBrowseHistory()
                if (response.isSuccess && response.data != null) {
                    val items = response.data.mapNotNull { item ->
                        val timeStr = (item["createTime"] as? String ?: "").replace("T", " ").substringBefore(".")
                        val serverTime = try { LocalDateTime.parse(timeStr, dtf) } catch (_: Exception) { null } ?: return@mapNotNull null
                        // 将服务端时区时间转为设备本地时间
                        val time = serverTime.atZone(serverZone).withZoneSameInstant(deviceZone).toLocalDateTime()
                        HistoryItem(
                            id = (item["id"] as? Number)?.toLong() ?: 0L,
                            productId = (item["productId"] as? Number)?.toLong() ?: 0L,
                            productName = item["productName"] as? String ?: "商品",
                            productImage = item["productImage"] as? String,
                            productPrice = when (val price = item["productPrice"]) {
                                is Number -> "¥${price}"
                                is String -> price
                                else -> "¥0"
                            },
                            browseTime = time,
                            source = item["source"] as? String
                        )
                    }
                    val groups = groupByDate(items)
                    _uiState.value = _uiState.value.copy(isLoading = false, groups = groups)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = response.message)
                }
            } catch (e: Exception) {
                Log.e("HistoryVM", "Load history failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "加载失败: ${e.message}")
            }
        }
    }

    private fun groupByDate(items: List<HistoryItem>): List<HistoryGroup> {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val monthStart = today.withDayOfMonth(1)

        val labelMap = linkedMapOf<String, MutableList<HistoryItem>>()
        for (item in items) {
            val date = item.browseTime.toLocalDate()
            val label = when {
                date == today -> "今天"
                date == yesterday -> "昨天"
                date >= weekStart -> "本周"
                date >= monthStart -> "本月"
                else -> "更早"
            }
            labelMap.getOrPut(label) { mutableListOf() }.add(item)
        }
        return labelMap.map { (label, list) -> HistoryGroup(label, list) }
    }

    fun clearHistory() {
        _uiState.value = _uiState.value.copy(groups = emptyList())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
