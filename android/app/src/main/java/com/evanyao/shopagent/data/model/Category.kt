package com.evanyao.shopagent.data.model

data class Category(
    val id: Long,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createTime: String? = null
)
