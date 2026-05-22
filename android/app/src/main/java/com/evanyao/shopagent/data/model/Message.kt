package com.evanyao.shopagent.data.model

data class Message(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val productCards: List<Product>? = null,
    val feedbackType: Int? = null,
    val createTime: String? = null
)
