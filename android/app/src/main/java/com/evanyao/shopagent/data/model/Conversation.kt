package com.evanyao.shopagent.data.model

data class Conversation(
    val id: Long,
    val userId: Long,
    val title: String? = null,
    val isPinned: Boolean = false,
    val createTime: String? = null,
    val updateTime: String? = null
)
