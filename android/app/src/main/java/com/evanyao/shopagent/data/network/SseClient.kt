package com.evanyao.shopagent.data.network

import android.util.Log
import com.evanyao.shopagent.data.TokenManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SseEvent(
    val type: String,
    val content: String,
    val taskType: String? = null,
    val productCards: Any? = null
)

class SseClient(
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "SseClient"
        private const val BASE_URL = "http://10.0.2.2:8080"
        private const val STREAM_PATH = "/api/chat/stream/messages"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 5L
    }

    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private val sseFactory = EventSources.createFactory(sseClient)

    fun streamMessage(
        userId: Long,
        conversationId: Long,
        content: String,
        username: String? = null,
        gender: String? = null,
        skinType: String? = null,
        preferenceTags: List<String>? = null
    ): Flow<SseEvent> = callbackFlow {
        val jsonBody = JSONObject().apply {
            put("userId", userId)
            put("conversationId", conversationId)
            put("content", content)
            if (username != null) put("username", username)
            if (gender != null) put("gender", gender)
            if (skinType != null) put("skinType", skinType)
            if (preferenceTags != null) {
                put("preferenceTags", org.json.JSONArray(preferenceTags))
            }
        }

        val body = jsonBody.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url("$BASE_URL$STREAM_PATH")
            .post(body)
            .header("Accept", "text/event-stream")

        // 添加 JWT token
        val token = runCatching {
            kotlinx.coroutines.runBlocking { tokenManager.getToken() }
        }.getOrNull()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        Log.d(TAG, "Starting SSE stream: conversationId=$conversationId")

        val eventSource = sseFactory.newEventSource(request, object : EventSourceListener() {

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                Log.d(TAG, "SSE event: type=$type, data=${data.take(200)}")
                try {
                    val json = JSONObject(data)
                    val eventType = json.optString("type", type ?: "unknown")
                    val eventContent = json.optString("content", "")
                    val taskType = json.optString("task_type", null)
                    val productCards = json.opt("product_cards")

                    val event = SseEvent(
                        type = eventType,
                        content = eventContent,
                        taskType = taskType,
                        productCards = productCards
                    )
                    if (eventType == "product_cards") {
                        Log.d(TAG, "Product cards event: $productCards")
                    }

                    val result = trySend(event)
                    if (result.isFailure) {
                        Log.w(TAG, "Failed to send event: ${result.exceptionOrNull()?.message}")
                    }

                    // end 或 error 事件关闭流
                    if (eventType == "end" || eventType == "error") {
                        close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse SSE data: $data", e)
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                val errorMsg = when {
                    response != null -> "HTTP ${response.code}: ${response.message}"
                    t != null -> t.message ?: "Unknown error"
                    else -> "Connection failed"
                }
                Log.e(TAG, "SSE failure: $errorMsg", t)

                trySend(SseEvent(type = "error", content = "连接中断：$errorMsg"))
                close(t ?: Exception(errorMsg))
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE connection closed")
                close()
            }
        })

        awaitClose {
            Log.d(TAG, "Cancelling SSE stream")
            eventSource.cancel()
        }
    }
}
