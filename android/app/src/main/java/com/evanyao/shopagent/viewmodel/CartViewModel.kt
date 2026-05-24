package com.evanyao.shopagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.TokenManager
import com.evanyao.shopagent.data.model.CartItem
import com.evanyao.shopagent.data.model.ProductSku
import com.evanyao.shopagent.data.repository.CartRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CartUiState(
    val cartItems: List<CartItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedItems: Set<Long> = emptySet(),
    val toastMessage: String? = null,
    val editingSkuItem: CartItem? = null,
    val editingSkus: List<ProductSku> = emptyList()
) {
    val totalPrice: Double
        get() = cartItems.sumOf { it.totalPrice.toDouble() }

    val selectedItemCount: Int
        get() = selectedItems.size

    val isCheckedAll: Boolean
        get() = cartItems.isNotEmpty() && cartItems.all { selectedItems.contains(it.productId) }
}

class CartViewModel(
    private val cartRepository: CartRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState

    init {
        loadCartList()
    }

    fun loadCartList() {
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = cartRepository.getCartList(userId)
                if (response.isSuccess && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        cartItems = response.data,
                        isLoading = false,
                        errorMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载购物车失败：${e.message}"
                )
            }
        }
    }

    fun addToCart(productId: Long, skuId: Long? = null) {
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = cartRepository.addItem(userId, productId, skuId)
                if (response.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        toastMessage = "已添加到购物车"
                    )
                    loadCartList()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "添加失败：${e.message}"
                )
            }
        }
    }

    fun updateQuantity(productId: Long, quantity: Int) {
        if (quantity < 1) return
        // 先本地更新，避免闪烁
        val currentItems = _uiState.value.cartItems.toMutableList()
        val index = currentItems.indexOfFirst { it.productId == productId }
        if (index != -1) {
            val oldItem = currentItems[index]
            currentItems[index] = oldItem.copy(quantity = quantity)
            _uiState.value = _uiState.value.copy(cartItems = currentItems)
        }
        // 同步到后端
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            try {
                val response = cartRepository.updateQuantity(userId, productId, quantity)
                if (!response.isSuccess) {
                    // 失败时回滚
                    loadCartList()
                    _uiState.value = _uiState.value.copy(errorMessage = response.message)
                }
            } catch (e: Exception) {
                loadCartList()
                _uiState.value = _uiState.value.copy(errorMessage = "更新失败：${e.message}")
            }
        }
    }

    fun removeFromCart(cartItemId: Long) {
        // 先本地移除，避免闪烁
        val currentItems = _uiState.value.cartItems.toMutableList()
        val removedItem = currentItems.find { it.id == cartItemId }
        currentItems.removeAll { it.id == cartItemId }
        val currentSelected = if (removedItem != null) {
            _uiState.value.selectedItems - removedItem.productId
        } else {
            _uiState.value.selectedItems
        }
        _uiState.value = _uiState.value.copy(
            cartItems = currentItems,
            selectedItems = currentSelected
        )
        // 同步到后端
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            try {
                // 需要根据 cartItemId 删除，但后端接口是按 productId 删除
                // 这里先使用 productId，后续可能需要修改后端接口
                if (removedItem != null) {
                    val response = cartRepository.removeItem(userId, removedItem.productId)
                    if (!response.isSuccess) {
                        loadCartList()
                        _uiState.value = _uiState.value.copy(errorMessage = response.message)
                    }
                }
            } catch (e: Exception) {
                loadCartList()
                _uiState.value = _uiState.value.copy(errorMessage = "删除失败：${e.message}")
            }
        }
    }

    fun toggleItemSelection(productId: Long) {
        val currentSelected = _uiState.value.selectedItems
        val updated = if (currentSelected.contains(productId)) {
            currentSelected - productId
        } else {
            currentSelected + productId
        }
        _uiState.value = _uiState.value.copy(selectedItems = updated)
    }

    fun toggleSelectAll() {
        val current = _uiState.value
        val updated = if (current.isCheckedAll) {
            emptySet()
        } else {
            current.cartItems.map { it.productId }.toSet()
        }
        _uiState.value = _uiState.value.copy(selectedItems = updated)
    }

    fun toggleShopSelectAll() {
        // 目前只有一个店铺，等同于全选
        toggleSelectAll()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun startEditSku(cartItem: CartItem) {
        viewModelScope.launch {
            try {
                val response = cartRepository.getProductSkus(cartItem.productId)
                if (response.isSuccess && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        editingSkuItem = cartItem,
                        editingSkus = response.data
                    )
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "获取规格失败")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "获取规格失败：${e.message}")
            }
        }
    }

    fun cancelEditSku() {
        _uiState.value = _uiState.value.copy(
            editingSkuItem = null,
            editingSkus = emptyList()
        )
    }

    fun confirmUpdateSku(newSkuId: Long) {
        val editingItem = _uiState.value.editingSkuItem ?: return
        val oldSkuId = editingItem.skuId
        val editingSkus = _uiState.value.editingSkus
        val newSku = editingSkus.find { it.id == newSkuId }

        // 先关闭弹窗
        _uiState.value = _uiState.value.copy(
            editingSkuItem = null,
            editingSkus = emptyList()
        )

        // 本地更新
        if (newSku != null) {
            val currentItems = _uiState.value.cartItems.toMutableList()
            val index = currentItems.indexOfFirst {
                it.productId == editingItem.productId && it.id == editingItem.id
            }
            if (index != -1) {
                currentItems[index] = currentItems[index].copy(skuId = newSkuId, sku = newSku)
                _uiState.value = _uiState.value.copy(cartItems = currentItems)
            }
        }

        // 同步到后端
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            try {
                val response = cartRepository.updateSku(userId, editingItem.productId, oldSkuId ?: 0L, newSkuId)
                if (response.isSuccess) {
                    _uiState.value = _uiState.value.copy(toastMessage = "规格已更新")
                } else {
                    loadCartList()
                    _uiState.value = _uiState.value.copy(errorMessage = response.message)
                }
            } catch (e: Exception) {
                loadCartList()
                _uiState.value = _uiState.value.copy(errorMessage = "更新失败：${e.message}")
            }
        }
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun refreshOnLogin() {
        _uiState.value = CartUiState()
        loadCartList()
    }
}
