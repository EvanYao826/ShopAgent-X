package com.evanyao.shopagent.data.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class CartItem(
    val id: Long,
    val userId: Long,
    val productId: Long,
    val skuId: Long? = null,
    val quantity: Int,
    @SerializedName("create_time")
    val createTime: String?,
    @SerializedName("update_time")
    val updateTime: String?,
    // 商品信息（关联查询）
    val product: Product?,
    // SKU 信息
    val sku: ProductSku? = null
) {
    val totalPrice: BigDecimal
        get() {
            val price = sku?.price ?: product?.basePrice ?: BigDecimal.ZERO
            return price.multiply(BigDecimal(quantity))
        }

    // 显示规格文本
    val skuText: String
        get() {
            if (sku?.properties.isNullOrEmpty()) return "默认"
            return sku?.properties?.entries?.joinToString(" ") { "${it.key}: ${it.value}" } ?: "默认"
        }
}
