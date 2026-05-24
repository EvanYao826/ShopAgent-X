package com.evanyao.shopagent.data.repository

import com.evanyao.shopagent.data.model.Category
import com.evanyao.shopagent.data.model.PageResponse
import com.evanyao.shopagent.data.model.Product
import com.evanyao.shopagent.data.model.Result as ApiResult
import com.evanyao.shopagent.data.network.api.CategoryApi
import com.evanyao.shopagent.data.network.api.ProductApi

class ProductRepository(
    private val productApi: ProductApi,
    private val categoryApi: CategoryApi
) {

    suspend fun getProducts(
        categoryId: Long? = null,
        page: Int = 1,
        size: Int = 20
    ): ApiResult<PageResponse<Product>> {
        return productApi.getProducts(categoryId, page, size)
    }

    suspend fun getProductDetail(id: Long): ApiResult<Map<String, Any>> {
        return productApi.getProductDetail(id)
    }

    suspend fun searchProducts(keyword: String, limit: Int = 20): ApiResult<List<Product>> {
        return productApi.searchProducts(keyword, limit)
    }

    suspend fun getCategories(): ApiResult<List<Category>> {
        return categoryApi.getCategories()
    }

    // 浏览历史
    suspend fun recordBrowse(userId: Long, productId: Long, source: String = "detail") {
        val history = mapOf(
            "userId" to userId,
            "productId" to productId,
            "source" to source
        )
        productApi.recordBrowse(history)
    }

    // 收藏
    suspend fun addFavorite(userId: Long, productId: Long) {
        productApi.addFavorite(userId, productId)
    }

    suspend fun removeFavorite(userId: Long, productId: Long) {
        productApi.removeFavorite(userId, productId)
    }

    suspend fun getFavoriteList(userId: Long): ApiResult<List<Map<String, Any>>> {
        return productApi.getFavoriteList(userId)
    }

    suspend fun getBrowseHistory(userId: Long): ApiResult<List<Map<String, Any>>> {
        return productApi.getBrowseHistory(userId)
    }
}
