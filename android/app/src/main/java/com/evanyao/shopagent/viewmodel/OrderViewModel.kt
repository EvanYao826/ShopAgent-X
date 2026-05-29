package com.evanyao.shopagent.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.model.CreateOrderItem
import com.evanyao.shopagent.data.model.CreateOrderRequest
import com.evanyao.shopagent.data.model.Order
import com.evanyao.shopagent.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OrderUiState(
    val orders: List<Order> = emptyList(),
    val currentOrder: Order? = null,
    val selectedStatus: Int? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val createSuccess: Boolean = false,
    val paymentDeadlines: Map<Long, Long> = emptyMap()
) {
    val currentPaymentDeadline: Long
        get() = currentOrder?.let { paymentDeadlines[it.id] } ?: 0L
}

class OrderViewModel(private val orderRepository: OrderRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState

    fun loadOrders(status: Int? = null, reset: Boolean = false) {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return

        val page = if (reset) 1 else state.currentPage
        _uiState.value = state.copy(
            selectedStatus = status,
            isLoading = reset,
            isLoadingMore = !reset,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val response = orderRepository.getOrderList(status, page)
                if (response.isSuccess && response.data != null) {
                    val pageData = response.data
                    val newOrders = if (reset) {
                        pageData.records
                    } else {
                        state.orders + pageData.records
                    }
                    _uiState.value = _uiState.value.copy(
                        orders = newOrders,
                        currentPage = pageData.current + 1,
                        hasMore = pageData.current < pageData.pages,
                        isLoading = false,
                        isLoadingMore = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                Log.e("OrderVM", "Load orders failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "加载失败: ${e.message}"
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore) return
        loadOrders(state.selectedStatus, reset = false)
    }

    fun selectStatus(status: Int?) {
        if (_uiState.value.selectedStatus == status) return
        _uiState.value = _uiState.value.copy(
            orders = emptyList(),
            currentPage = 1,
            hasMore = true
        )
        loadOrders(status, reset = true)
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            orders = emptyList(),
            currentPage = 1,
            hasMore = true,
            errorMessage = null
        )
        loadOrders(_uiState.value.selectedStatus, reset = true)
    }

    fun loadOrderDetail(orderId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val response = orderRepository.getOrderDetail(orderId)
                if (response.isSuccess && response.data != null) {
                    val order = response.data
                    val state = _uiState.value
                    // 待付款订单：首次设置截止时间，之后不变
                    val deadlines = state.paymentDeadlines.toMutableMap()
                    if (order.status == Order.STATUS_PENDING_PAY && !deadlines.containsKey(order.id)) {
                        deadlines[order.id] = calculateDeadline(order.createTime)
                    }
                    _uiState.value = state.copy(
                        currentOrder = order,
                        isLoading = false,
                        paymentDeadlines = deadlines
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                Log.e("OrderVM", "Load order detail failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载失败: ${e.message}"
                )
            }
        }
    }

    private fun calculateDeadline(createTime: String?): Long {
        if (createTime == null) return System.currentTimeMillis() + 600_000
        return try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val dateTime = java.time.LocalDateTime.parse(createTime, formatter)
            dateTime.plusMinutes(10)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis() + 600_000
        }
    }

    fun createOrder(addressId: Long, items: List<CreateOrderItem>, remark: String? = null,
                    receiverName: String? = null, receiverPhone: String? = null) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, createSuccess = false)

        viewModelScope.launch {
            try {
                val request = CreateOrderRequest(addressId, items, remark, receiverName, receiverPhone)
                val response = orderRepository.createOrder(request)
                if (response.isSuccess && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        currentOrder = response.data,
                        isLoading = false,
                        createSuccess = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                Log.e("OrderVM", "Create order failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "创建订单失败: ${e.message}"
                )
            }
        }
    }

    fun payOrder(orderId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val response = orderRepository.payOrder(orderId)
                if (response.isSuccess && response.data != null) {
                    val deadlines = _uiState.value.paymentDeadlines.toMutableMap()
                    deadlines.remove(orderId)
                    _uiState.value = _uiState.value.copy(
                        currentOrder = response.data,
                        isLoading = false,
                        paymentDeadlines = deadlines
                    )
                    refresh()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                Log.e("OrderVM", "Pay order failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "支付失败: ${e.message}"
                )
            }
        }
    }

    fun cancelOrder(orderId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val response = orderRepository.cancelOrder(orderId)
                if (response.isSuccess && response.data != null) {
                    val deadlines = _uiState.value.paymentDeadlines.toMutableMap()
                    deadlines.remove(orderId)
                    _uiState.value = _uiState.value.copy(
                        currentOrder = response.data,
                        isLoading = false,
                        paymentDeadlines = deadlines
                    )
                    refresh()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                Log.e("OrderVM", "Cancel order failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "取消订单失败: ${e.message}"
                )
            }
        }
    }

    fun confirmReceive(orderId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val response = orderRepository.confirmReceive(orderId)
                if (response.isSuccess && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        currentOrder = response.data,
                        isLoading = false
                    )
                    refresh()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                Log.e("OrderVM", "Confirm receive failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "确认收货失败: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearCreateSuccess() {
        _uiState.value = _uiState.value.copy(createSuccess = false)
    }

}
