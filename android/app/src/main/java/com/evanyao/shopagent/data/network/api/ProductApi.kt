package com.evanyao.shopagent.data.network.api

import com.evanyao.shopagent.data.model.PageResponse
import com.evanyao.shopagent.data.model.Product
import com.evanyao.shopagent.data.model.Result
import retrofit2.http.*

interface ProductApi {

    @GET("api/product/list")
    suspend fun getProducts(
        @Query("categoryId") categoryId: Long? = null,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null
    ): Result<PageResponse<Product>>

    @GET("api/product/{id}")
    suspend fun getProductDetail(@Path("id") id: Long): Result<@JvmSuppressWildcards Map<String, Any>>

    @GET("api/product/search")
    suspend fun searchProducts(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 20
    ): Result<List<Product>>

    // 浏览历史
    @POST("api/recommend/browse")
    suspend fun recordBrowse(@Body history: @JvmSuppressWildcards Map<String, Any>): Result<Void>

    // 收藏
    @POST("api/recommend/favorite/add")
    suspend fun addFavorite(@Query("productId") productId: Long): Result<Void>

    @POST("api/recommend/favorite/remove")
    suspend fun removeFavorite(@Query("productId") productId: Long): Result<Void>

    @GET("api/recommend/favorite/list")
    suspend fun getFavoriteList(): Result<List<@JvmSuppressWildcards Map<String, Any>>>

    @GET("api/recommend/browse/history")
    suspend fun getBrowseHistory(): Result<List<@JvmSuppressWildcards Map<String, Any>>>

    @DELETE("api/recommend/browse/history/{id}")
    suspend fun deleteBrowseHistory(@Path("id") historyId: Long): Result<String>
}
