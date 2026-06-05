package com.evanyao.shopagent.data.model

data class Message(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val productCards: List<Product>? = null,
    val feedbackType: Int? = null,
    val createTime: String? = null,
    val messageType: String? = null,
    val confirmCard: ConfirmCard? = null,
    val cartSelection: CartSelection? = null
)

data class ConfirmCard(
    val message: String,
    val action: String,
    val product: Product? = null,
    val products: List<Product>? = null,
    val buttons: List<ConfirmButton>,
    val answered: Boolean = false
)

data class ConfirmButton(
    val type: String,
    val label: String
)

data class CartSelection(
    val message: String,
    val items: List<Product>
)
