package com.evanyao.shopagent.data.network.api

import com.evanyao.shopagent.data.model.CreateOrderRequest
import com.evanyao.shopagent.data.model.Order
import com.evanyao.shopagent.data.model.PageResponse
import com.evanyao.shopagent.data.model.Result
import retrofit2.http.*

interface OrderApi {

    @POST("api/order/create")
    suspend fun createOrder(@Body request: CreateOrderRequest): Result<Order>

    @GET("api/order/list")
    suspend fun getOrderList(
        @Query("status") status: Int? = null,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 10
    ): Result<PageResponse<Order>>

    @GET("api/order/{id}")
    suspend fun getOrderDetail(@Path("id") orderId: Long): Result<Order>

    @PUT("api/order/{id}/pay")
    suspend fun payOrder(@Path("id") orderId: Long): Result<Order>

    @PUT("api/order/{id}/cancel")
    suspend fun cancelOrder(@Path("id") orderId: Long): Result<Order>

    @PUT("api/order/{id}/receive")
    suspend fun confirmReceive(@Path("id") orderId: Long): Result<Order>

    @DELETE("api/order/{id}")
    suspend fun deleteOrder(@Path("id") orderId: Long): Result<String>
}
