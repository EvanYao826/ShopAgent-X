package com.evanyao.shopagent.data.model

data class ProfileUpdateRequest(
    val username: String? = null,
    val avatarUrl: String? = null,
    val gender: Int? = null,
    val ageRange: String? = null,
    val skinType: String? = null,
    val preferenceTags: List<String>? = null
)
