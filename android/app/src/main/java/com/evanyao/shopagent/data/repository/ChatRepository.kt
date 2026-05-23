package com.evanyao.shopagent.data.repository

import com.evanyao.shopagent.data.model.ChatRequest
import com.evanyao.shopagent.data.model.Conversation
import com.evanyao.shopagent.data.model.Message
import com.evanyao.shopagent.data.model.Result as ApiResult
import com.evanyao.shopagent.data.network.api.ChatApi

class ChatRepository(private val chatApi: ChatApi) {

    suspend fun createConversation(userId: Long, title: String? = null): ApiResult<Conversation> {
        return chatApi.createConversation(userId, title)
    }

    suspend fun getConversations(userId: Long): ApiResult<List<Conversation>> {
        return chatApi.getConversations(userId)
    }

    suspend fun sendMessage(userId: Long, conversationId: Long, content: String): ApiResult<Message> {
        return chatApi.sendMessage(ChatRequest(userId, conversationId, content))
    }

    suspend fun getMessages(conversationId: Long): ApiResult<List<Message>> {
        return chatApi.getMessages(conversationId)
    }

    suspend fun deleteConversation(id: Long): ApiResult<String> {
        return chatApi.deleteConversation(id)
    }

    suspend fun updateConversation(id: Long, conversation: Conversation): ApiResult<Conversation> {
        return chatApi.updateConversation(id, conversation)
    }
}
