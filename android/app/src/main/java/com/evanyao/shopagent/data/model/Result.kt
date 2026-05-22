package com.evanyao.shopagent.data.model

data class Result<T>(
    val code: Int,
    val message: String,
    val data: T?
) {
    val isSuccess: Boolean get() = code == 200
}
