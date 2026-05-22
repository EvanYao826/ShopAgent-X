package com.evanyao.shopagent.data.model

import java.math.BigDecimal

data class Product(
    val id: Long,
    val productCode: String? = null,
    val categoryId: Long? = null,
    val title: String,
    val brand: String? = null,
    val subCategory: String? = null,
    val basePrice: BigDecimal,
    val imageUrl: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val rating: BigDecimal? = null,
    val reviewCount: Int = 0,
    val salesCount: Int = 0,
    val status: Int = 1,
    val createTime: String? = null,
    val updateTime: String? = null
)
