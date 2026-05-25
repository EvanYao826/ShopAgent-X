package com.evanyao.shopagent.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FavoriteUiState(
    val isLoading: Boolean = false,
    val favorites: List<FavoriteItem> = emptyList(),
    val errorMessage: String? = null
)

data class FavoriteItem(
    val id: Long,
    val productId: Long,
    val productName: String,
    val productImage: String?,
    val productPrice: String,
    val createTime: String
)

class FavoriteViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoriteUiState())
    val uiState: StateFlow<FavoriteUiState> = _uiState

    fun loadFavorites() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = productRepository.getFavoriteList()
                if (response.isSuccess && response.data != null) {
                    val favorites = response.data.map { item ->
                        FavoriteItem(
                            id = (item["id"] as? Number)?.toLong() ?: 0L,
                            productId = (item["productId"] as? Number)?.toLong() ?: 0L,
                            productName = item["productName"] as? String ?: "商品",
                            productImage = item["productImage"] as? String,
                            productPrice = when (val price = item["productPrice"]) {
                                is Number -> "¥${price}"
                                is String -> price
                                else -> "¥0"
                            },
                            createTime = (item["createTime"] as? String ?: "").replace("T", " ").substringBefore(".")
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        favorites = favorites
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                Log.e("FavoriteVM", "Load favorites failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载失败: ${e.message}"
                )
            }
        }
    }

    fun removeFavorite(productId: Long) {
        viewModelScope.launch {
            try {
                productRepository.removeFavorite(productId)
                // 更新本地状态
                _uiState.value = _uiState.value.copy(
                    favorites = _uiState.value.favorites.filter { it.productId != productId }
                )
            } catch (e: Exception) {
                Log.e("FavoriteVM", "Remove favorite failed", e)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
