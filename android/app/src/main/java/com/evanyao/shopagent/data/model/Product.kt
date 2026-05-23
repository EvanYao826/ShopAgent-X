package com.evanyao.shopagent.data.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class Product(
    @SerializedName(value = "id", alternate = ["product_id"])
    val id: Long,
    val productCode: String? = null,
    val categoryId: Long? = null,
    val title: String,
    val brand: String? = null,
    @SerializedName(value = "subCategory", alternate = ["sub_category"])
    val subCategory: String? = null,
    @SerializedName(value = "basePrice", alternate = ["price", "base_price"])
    val basePrice: BigDecimal,
    @SerializedName(value = "imageUrl", alternate = ["image_url"])
    val imageUrl: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val rating: BigDecimal? = null,
    @SerializedName(value = "reviewCount", alternate = ["review_count"])
    val reviewCount: Int = 0,
    @SerializedName(value = "salesCount", alternate = ["sales_count"])
    val salesCount: Int = 0,
    val status: Int = 1,
    val createTime: String? = null,
    val updateTime: String? = null
)
