package com.evanyao.shopagent.data.model

data class RegisterRequest(
    val phone: String,
    val code: String,
    val password: String,
    val username: String
)
