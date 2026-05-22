package com.evanyao.shopagent.data.model

data class ChatRequest(
    val userId: Long,
    val conversationId: Long,
    val content: String
)
