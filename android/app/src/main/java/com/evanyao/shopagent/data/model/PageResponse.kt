package com.evanyao.shopagent.data.model

data class PageResponse<T>(
    val records: List<T>,
    val total: Long = 0,
    val pages: Int = 0,
    val current: Int = 1,
    val size: Int = 20
)
