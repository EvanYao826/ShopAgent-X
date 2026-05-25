package com.evanyao.shopagent.data.repository

import com.evanyao.shopagent.data.model.ChatRequest
import com.evanyao.shopagent.data.model.Conversation
import com.evanyao.shopagent.data.model.Message
import com.evanyao.shopagent.data.model.Result as ApiResult
import com.evanyao.shopagent.data.network.SseClient
import com.evanyao.shopagent.data.network.SseEvent
import com.evanyao.shopagent.data.network.api.ChatApi
import com.evanyao.shopagent.data.network.api.FeedbackRequest
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatApi: ChatApi,
    private val sseClient: SseClient
) {

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

    fun streamMessage(
        userId: Long,
        conversationId: Long,
        content: String,
        username: String? = null,
        gender: String? = null,
        skinType: String? = null,
        preferenceTags: List<String>? = null
    ): Flow<SseEvent> {
        return sseClient.streamMessage(userId, conversationId, content, username, gender, skinType, preferenceTags)
    }

    suspend fun submitFeedback(messageId: Long, feedbackType: Int): ApiResult<Message> {
        return chatApi.submitFeedback(FeedbackRequest(messageId, feedbackType))
    }
}
