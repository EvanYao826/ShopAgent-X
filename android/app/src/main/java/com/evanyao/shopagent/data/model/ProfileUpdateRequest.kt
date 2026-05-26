package com.evanyao.shopagent.data.model

data class ProfileUpdateRequest(
    val userId: Long,
    val gender: Int,
    val ageRange: String,
    val skinType: String,
    val preferenceTags: List<String>
)
