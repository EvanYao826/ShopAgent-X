package com.evanyao.shopagent.data.repository

import com.evanyao.shopagent.data.model.CartItem
import com.evanyao.shopagent.data.model.ProductSku
import com.evanyao.shopagent.data.model.Result as ApiResult
import com.evanyao.shopagent.data.network.api.CartApi

class CartRepository(private val cartApi: CartApi) {

    suspend fun addItem(userId: Long, productId: Long, skuId: Long? = null): ApiResult<CartItem> {
        return cartApi.addItem(userId, productId, skuId)
    }

    suspend fun removeItem(userId: Long, productId: Long): ApiResult<Void> {
        return cartApi.removeItem(userId, productId)
    }

    suspend fun updateQuantity(userId: Long, productId: Long, quantity: Int): ApiResult<CartItem> {
        return cartApi.updateQuantity(userId, productId, quantity)
    }

    suspend fun updateSku(userId: Long, productId: Long, oldSkuId: Long, newSkuId: Long): ApiResult<Void> {
        return cartApi.updateSku(userId, productId, oldSkuId, newSkuId)
    }

    suspend fun getProductSkus(productId: Long): ApiResult<List<ProductSku>> {
        return cartApi.getProductSkus(productId)
    }

    suspend fun getCartList(userId: Long): ApiResult<List<CartItem>> {
        return cartApi.getCartList(userId)
    }
}
