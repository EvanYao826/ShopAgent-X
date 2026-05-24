package com.evanyao.shopagent.data.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class ProductSku(
    val id: Long,
    @SerializedName(value = "productId", alternate = ["product_id"])
    val productId: Long,
    @SerializedName(value = "skuCode", alternate = ["sku_code"])
    val skuCode: String? = null,
    val properties: Map<String, String>? = null,
    val price: BigDecimal,
    val stock: Int = 0,
    @SerializedName(value = "isDefault", alternate = ["is_default"])
    val isDefault: Boolean = false,
    val createTime: String? = null
) {
    // 显示规格文本
    val propertiesText: String
        get() {
            if (properties.isNullOrEmpty()) return ""
            return properties.entries.joinToString(" ") { "${it.key}: ${it.value}" }
        }
}
