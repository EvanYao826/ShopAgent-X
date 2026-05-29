package com.evanyao.shopagent.data.model

data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)
