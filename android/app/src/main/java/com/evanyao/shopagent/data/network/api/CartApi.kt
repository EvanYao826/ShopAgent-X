package com.evanyao.shopagent.data.network.api

import com.evanyao.shopagent.data.model.CartItem
import com.evanyao.shopagent.data.model.ProductSku
import com.evanyao.shopagent.data.model.Result
import retrofit2.http.*

interface CartApi {

    @POST("api/cart/add")
    suspend fun addItem(
        @Query("userId") userId: Long,
        @Query("productId") productId: Long,
        @Query("skuId") skuId: Long? = null
    ): Result<CartItem>

    @DELETE("api/cart/remove")
    suspend fun removeItem(
        @Query("userId") userId: Long,
        @Query("productId") productId: Long
    ): Result<Void>

    @PUT("api/cart/update")
    suspend fun updateQuantity(
        @Query("userId") userId: Long,
        @Query("productId") productId: Long,
        @Query("quantity") quantity: Int
    ): Result<CartItem>

    @PUT("api/cart/updateSku")
    suspend fun updateSku(
        @Query("userId") userId: Long,
        @Query("productId") productId: Long,
        @Query("oldSkuId") oldSkuId: Long,
        @Query("newSkuId") newSkuId: Long
    ): Result<Void>

    @GET("api/product/{productId}/skus")
    suspend fun getProductSkus(@Path("productId") productId: Long): Result<List<ProductSku>>

    @GET("api/cart/list")
    suspend fun getCartList(@Query("userId") userId: Long): Result<List<CartItem>>
}
