package com.evanyao.shopagent.data.network.api

import com.evanyao.shopagent.data.model.ChatRequest
import com.evanyao.shopagent.data.model.Conversation
import com.evanyao.shopagent.data.model.Message
import com.evanyao.shopagent.data.model.Result
import okhttp3.MultipartBody
import retrofit2.http.*

interface ChatApi {

    @POST("api/chat/conversations")
    suspend fun createConversation(
        @Query("userId") userId: Long,
        @Query("title") title: String? = null
    ): Result<Conversation>

    @GET("api/chat/conversations")
    suspend fun getConversations(@Query("userId") userId: Long): Result<List<Conversation>>

    @POST("api/chat/messages")
    suspend fun sendMessage(@Body request: ChatRequest): Result<Message>

    @GET("api/chat/messages")
    suspend fun getMessages(@Query("conversationId") conversationId: Long): Result<List<Message>>

    @DELETE("api/chat/conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: Long): Result<String>

    @PUT("api/chat/conversations/{id}")
    suspend fun updateConversation(
        @Path("id") id: Long,
        @Body conversation: Conversation
    ): Result<Conversation>

    @POST("api/chat/messages/feedback")
    suspend fun submitFeedback(
        @Body request: FeedbackRequest
    ): Result<Message>

    @POST("api/chat/messages/save")
    suspend fun saveMessage(
        @Body request: Map<String, Any>
    ): Result<Message>

    @Multipart
    @POST("api/chat/recognize-image")
    suspend fun recognizeImage(@Part file: MultipartBody.Part): Result<String>

    @Multipart
    @POST("api/chat/voice/recognize")
    suspend fun recognizeVoice(@Part file: MultipartBody.Part): Result<String>
}

data class FeedbackRequest(
    val messageId: Long,
    val feedbackType: Int
)
