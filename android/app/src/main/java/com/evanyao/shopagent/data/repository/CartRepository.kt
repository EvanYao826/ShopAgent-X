package com.evanyao.shopagent.data.repository

import com.evanyao.shopagent.data.model.CartItem
import com.evanyao.shopagent.data.model.ProductSku
import com.evanyao.shopagent.data.model.Result as ApiResult
import com.evanyao.shopagent.data.network.api.CartApi

class CartRepository(private val cartApi: CartApi) {

    suspend fun addItem(productId: Long, skuId: Long? = null): ApiResult<CartItem> {
        return cartApi.addItem(productId, skuId)
    }

    suspend fun removeItem(productId: Long): ApiResult<Void> {
        return cartApi.removeItem(productId)
    }

    suspend fun updateQuantity(productId: Long, quantity: Int): ApiResult<CartItem> {
        return cartApi.updateQuantity(productId, quantity)
    }

    suspend fun updateSku(productId: Long, oldSkuId: Long, newSkuId: Long): ApiResult<Void> {
        return cartApi.updateSku(productId, oldSkuId, newSkuId)
    }

    suspend fun getProductSkus(productId: Long): ApiResult<List<ProductSku>> {
        return cartApi.getProductSkus(productId)
    }

    suspend fun getCartList(): ApiResult<List<CartItem>> {
        return cartApi.getCartList()
    }
}
