package com.evanyao.shopagent.data.model

data class User(
    val id: Long,
    val username: String,
    val phone: String,
    val avatarUrl: String? = null,
    val gender: Int? = null,
    val ageRange: String? = null,
    val skinType: String? = null,
    val preferenceTags: List<String>? = null,
    val status: Int = 1,
    val createTime: String? = null,
    val updateTime: String? = null
)
