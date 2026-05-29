package com.evanyao.shopagent.data.repository

import com.evanyao.shopagent.data.model.CreateOrderRequest
import com.evanyao.shopagent.data.model.Order
import com.evanyao.shopagent.data.model.PageResponse
import com.evanyao.shopagent.data.model.Result as ApiResult
import com.evanyao.shopagent.data.network.api.OrderApi

class OrderRepository(private val orderApi: OrderApi) {

    suspend fun createOrder(request: CreateOrderRequest): ApiResult<Order> {
        return orderApi.createOrder(request)
    }

    suspend fun getOrderList(
        status: Int? = null,
        page: Int = 1,
        size: Int = 10
    ): ApiResult<PageResponse<Order>> {
        return orderApi.getOrderList(status, page, size)
    }

    suspend fun getOrderDetail(orderId: Long): ApiResult<Order> {
        return orderApi.getOrderDetail(orderId)
    }

    suspend fun payOrder(orderId: Long): ApiResult<Order> {
        return orderApi.payOrder(orderId)
    }

    suspend fun cancelOrder(orderId: Long): ApiResult<Order> {
        return orderApi.cancelOrder(orderId)
    }

    suspend fun confirmReceive(orderId: Long): ApiResult<Order> {
        return orderApi.confirmReceive(orderId)
    }

    suspend fun deleteOrder(orderId: Long): ApiResult<String> {
        return orderApi.deleteOrder(orderId)
    }
}
