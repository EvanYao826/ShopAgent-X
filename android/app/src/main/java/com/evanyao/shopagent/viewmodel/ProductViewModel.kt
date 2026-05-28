package com.evanyao.shopagent.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.model.Category
import com.evanyao.shopagent.data.model.Product
import com.evanyao.shopagent.data.repository.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProductDetailState(
    val product: Product? = null,
    val skus: List<Map<String, Any>> = emptyList(),
    val images: List<Map<String, Any>> = emptyList(),
    val reviews: List<Map<String, Any>> = emptyList(),
    val faqs: List<Map<String, Any>> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class ProductUiState(
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val selectedCategoryId: Long? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val productDetail: ProductDetailState = ProductDetailState(),
    val favoriteProductIds: List<Long>? = null
)

class ProductViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState

    private var searchJob: Job? = null

    init {
        loadCategories()
        loadProducts(reset = true)
    }

    private fun retryLoadCategories() {
        viewModelScope.launch {
            delay(2000)
            Log.d("ProductVM", "Retrying loadCategories after failure")
            loadCategories()
        }
    }

    private fun retryLoadProducts() {
        viewModelScope.launch {
            delay(1000)
            Log.d("ProductVM", "Retrying loadProducts after failure")
            loadProducts(reset = true)
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                val response = productRepository.getCategories()
                if (response.isSuccess && response.data != null) {
                    _uiState.value = _uiState.value.copy(categories = response.data)
                } else {
                    Log.e("ProductVM", "Load categories failed: ${response.code} ${response.message}")
                    // 加载失败时自动重试
                    if (_uiState.value.categories.isEmpty()) {
                        retryLoadCategories()
                    }
                }
            } catch (e: Exception) {
                Log.e("ProductVM", "Load categories failed", e)
                // 加载失败时自动重试
                if (_uiState.value.categories.isEmpty()) {
                    retryLoadCategories()
                }
            }
        }
    }

    fun loadProducts(reset: Boolean = false) {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return

        val page = if (reset) 1 else state.currentPage
        _uiState.value = state.copy(
            isLoading = reset,
            isLoadingMore = !reset,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                // 搜索模式
                if (state.searchQuery.isNotBlank()) {
                    val response = productRepository.searchProducts(state.searchQuery)
                    if (response.isSuccess && response.data != null) {
                        _uiState.value = _uiState.value.copy(
                            products = response.data,
                            isLoading = false,
                            isLoadingMore = false,
                            hasMore = false
                        )
                    } else {
                        Log.e("ProductVM", "Search failed: ${response.code} ${response.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = response.message
                        )
                    }
                    return@launch
                }

                // 正常列表模式
                Log.d("ProductVM", "Loading products: categoryId=${state.selectedCategoryId}, page=$page")
                val response = productRepository.getProducts(state.selectedCategoryId, page)
                Log.d("ProductVM", "Response: code=${response.code}, message=${response.message}, data=${response.data}")

                if (response.isSuccess && response.data != null) {
                    val pageData = response.data
                    val newProducts = if (reset) {
                        pageData.records
                    } else {
                        state.products + pageData.records
                    }
                    Log.d("ProductVM", "Loaded ${pageData.records.size} products, total: ${pageData.total}")
                    _uiState.value = _uiState.value.copy(
                        products = newProducts,
                        currentPage = pageData.current + 1,
                        hasMore = pageData.current < pageData.pages,
                        isLoading = false,
                        isLoadingMore = false
                    )
                } else {
                    Log.e("ProductVM", "Load failed: ${response.code} ${response.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                Log.e("ProductVM", "Exception loading products", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "加载失败: ${e.message}"
                )
                // 首次加载失败时自动重试
                if (_uiState.value.products.isEmpty()) {
                    retryLoadProducts()
                }
            }
        }
    }

    fun selectCategory(categoryId: Long?) {
        if (_uiState.value.selectedCategoryId == categoryId) return
        _uiState.value = _uiState.value.copy(
            selectedCategoryId = categoryId,
            searchQuery = "",
            products = emptyList(),
            currentPage = 1,
            hasMore = true
        )
        loadProducts(reset = true)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                products = emptyList(),
                currentPage = 1,
                hasMore = true
            )
            loadProducts(reset = true)
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            _uiState.value = _uiState.value.copy(
                products = emptyList(),
                currentPage = 1,
                hasMore = false
            )
            loadProducts(reset = true)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore || state.searchQuery.isNotBlank()) return
        loadProducts(reset = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearDetailError() {
        _uiState.value = _uiState.value.copy(
            productDetail = _uiState.value.productDetail.copy(errorMessage = null)
        )
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            products = emptyList(),
            currentPage = 1,
            hasMore = true,
            errorMessage = null
        )
        loadProducts(reset = true)
    }

    fun loadProductDetail(productId: Long) {
        _uiState.value = _uiState.value.copy(
            productDetail = ProductDetailState(isLoading = true)
        )

        viewModelScope.launch {
            try {
                val response = productRepository.getProductDetail(productId)
                if (response.isSuccess && response.data != null) {
                    val data = response.data
                    @Suppress("UNCHECKED_CAST")
                    _uiState.value = _uiState.value.copy(
                        productDetail = ProductDetailState(
                            product = parseProduct(data["product"]),
                            skus = data["skus"] as? List<Map<String, Any>> ?: emptyList(),
                            images = data["images"] as? List<Map<String, Any>> ?: emptyList(),
                            reviews = data["reviews"] as? List<Map<String, Any>> ?: emptyList(),
                            faqs = data["faqs"] as? List<Map<String, Any>> ?: emptyList(),
                            isLoading = false
                        )
                    )
                    // 上报浏览记录
                    productRepository.recordBrowse(productId, "detail")
                } else {
                    _uiState.value = _uiState.value.copy(
                        productDetail = ProductDetailState(
                            isLoading = false,
                            errorMessage = response.message
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("ProductVM", "Load product detail failed", e)
                _uiState.value = _uiState.value.copy(
                    productDetail = ProductDetailState(
                        isLoading = false,
                        errorMessage = "加载失败: ${e.message}"
                    )
                )
            }
        }
    }

    fun recordBrowse(productId: Long, source: String = "detail") {
        viewModelScope.launch {
            try {
                productRepository.recordBrowse(productId, source)
            } catch (e: Exception) {
                Log.e("ProductVM", "Record browse failed", e)
            }
        }
    }

    fun getFavoriteList() {
        viewModelScope.launch {
            try {
                val response = productRepository.getFavoriteList()
                if (response.isSuccess && response.data != null) {
                    val favoriteProductIds = response.data.map { (it["productId"] as? Number)?.toLong() }.filterNotNull()
                    _uiState.value = _uiState.value.copy(
                        favoriteProductIds = favoriteProductIds
                    )
                }
            } catch (e: Exception) {
                Log.e("ProductVM", "Get favorite list failed", e)
            }
        }
    }

    fun toggleFavorite(productId: Long) {
        val currentIds = _uiState.value.favoriteProductIds
        val isFavorite = currentIds?.contains(productId) == true
        // 乐观更新 UI
        _uiState.value = _uiState.value.copy(
            favoriteProductIds = if (isFavorite) {
                currentIds?.filter { it != productId }
            } else {
                (currentIds ?: emptyList()) + productId
            }
        )
        viewModelScope.launch {
            try {
                if (isFavorite) {
                    productRepository.removeFavorite(productId)
                } else {
                    productRepository.addFavorite(productId)
                }
            } catch (e: Exception) {
                Log.e("ProductVM", "Toggle favorite failed, rolling back", e)
                // 回滚 UI 状态
                _uiState.value = _uiState.value.copy(favoriteProductIds = currentIds)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseProduct(data: Any?): Product? {
        if (data !is Map<*, *>) return null
        return try {
            Product(
                id = (data["id"] as? Number)?.toLong() ?: return null,
                productCode = data["productCode"] as? String,
                categoryId = (data["categoryId"] as? Number)?.toLong(),
                title = data["title"] as? String ?: "",
                brand = data["brand"] as? String,
                subCategory = data["subCategory"] as? String,
                basePrice = java.math.BigDecimal(data["basePrice"]?.toString() ?: "0"),
                imageUrl = data["imageUrl"] as? String,
                description = data["description"] as? String,
                tags = data["tags"] as? String,
                rating = (data["rating"] as? Number)?.let { java.math.BigDecimal(it.toString()) },
                reviewCount = (data["reviewCount"] as? Number)?.toInt() ?: 0,
                salesCount = (data["salesCount"] as? Number)?.toInt() ?: 0,
                status = (data["status"] as? Number)?.toInt() ?: 1,
                createTime = data["createTime"] as? String,
                updateTime = data["updateTime"] as? String
            )
        } catch (e: Exception) {
            Log.e("ProductVM", "Parse product failed", e)
            null
        }
    }

    fun clearProductDetail() {
        _uiState.value = _uiState.value.copy(productDetail = ProductDetailState())
    }
}
