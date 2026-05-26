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
        @Query("size") size: Int = 20
    ): Result<PageResponse<Product>>

    @GET("api/product/{id}")
    suspend fun getProductDetail(@Path("id") id: Long): Result<Map<String, Any>>

    @GET("api/product/search")
    suspend fun searchProducts(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 20
    ): Result<List<Product>>
}
