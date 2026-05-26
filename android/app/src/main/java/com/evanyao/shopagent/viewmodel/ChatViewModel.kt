package com.evanyao.shopagent.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.TokenManager
import com.evanyao.shopagent.data.model.Conversation
import com.evanyao.shopagent.data.model.Message
import com.evanyao.shopagent.data.model.Product
import com.evanyao.shopagent.data.repository.ChatRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val errorMessage: String? = null,
    val userGender: Int? = null,
    val recommendations: List<String> = listOf(
        "推荐一款适合油皮的精华",
        "敏感肌可以用什么面膜？",
        "有没有好用的防晒霜？",
        "抗衰老护肤品推荐"
    )
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MAX_RETRY_COUNT = 2
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var streamJob: Job? = null
    private val gson = Gson()

    init {
        loadConversations()
        loadRecommendations()
        loadUserGender()
    }

    private fun loadUserGender() {
        viewModelScope.launch {
            val gender = tokenManager.getGender()?.toIntOrNull()
            _uiState.value = _uiState.value.copy(userGender = gender)
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            try {
                val response = chatRepository.getConversations(userId)
                if (response.isSuccess && response.data != null) {
                    val conversations = response.data
                    val currentId = _uiState.value.currentConversation?.id
                    val updatedCurrent = currentId?.let { id ->
                        conversations.find { it.id == id }
                    } ?: _uiState.value.currentConversation

                    _uiState.value = _uiState.value.copy(
                        conversations = conversations,
                        currentConversation = updatedCurrent,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载会话失败：${e.message}"
                )
            }
        }
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            val skinType = tokenManager.getSkinType()
            val tags = tokenManager.getPreferenceTags()
            val recs = generateRecommendations(skinType, tags)
            _uiState.value = _uiState.value.copy(recommendations = recs)
        }
    }

    private fun generateRecommendations(skinType: String?, tags: List<String>): List<String> {
        val recs = mutableListOf<String>()

        when (skinType) {
            "油性" -> recs.add("推荐一款适合油皮的控油精华")
            "干性" -> recs.add("推荐一款高保湿面霜，干皮救星")
            "混合型" -> recs.add("推荐适合混合肌的水乳套装")
            "敏感型" -> recs.add("敏感肌可以用什么温和面膜？")
            "中性" -> recs.add("推荐一款日常基础护肤套装")
        }

        val tagRecs = mapOf(
            "美妆护肤" to "有没有好用的防晒霜推荐？",
            "时尚穿搭" to "推荐几款百搭的通勤穿搭",
            "数码科技" to "性价比高的蓝牙耳机推荐",
            "运动健身" to "适合跑步的运动鞋推荐",
            "美食零食" to "好吃不贵的零食推荐",
            "家居生活" to "提升幸福感的家居好物",
            "母婴育儿" to "宝宝必备的洗护用品推荐",
            "图书文具" to "值得入手的高颜值文具"
        )
        for (tag in tags) {
            if (recs.size >= 4) break
            val rec = tagRecs[tag] ?: continue
            if (!recs.any { it.contains(rec.take(4)) }) {
                recs.add(rec)
            }
        }

        val defaults = listOf(
            "抗衰老护肤品推荐",
            "有没有平价好用的水乳推荐？",
            "适合学生党的护肤套装",
            "秋冬保湿身体乳推荐"
        )
        for (d in defaults) {
            if (recs.size >= 4) break
            if (!recs.any { it.contains(d.take(4)) }) {
                recs.add(d)
            }
        }

        return recs.take(4)
    }

    fun createConversation(title: String? = null) {
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = chatRepository.createConversation(userId, title)
                if (response.isSuccess && response.data != null) {
                    val newConversation = response.data
                    _uiState.value = _uiState.value.copy(
                        conversations = listOf(newConversation) + _uiState.value.conversations,
                        currentConversation = newConversation,
                        messages = emptyList(),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "创建会话失败：${e.message}"
                )
            }
        }
    }

    fun createConversationAndSendMessage(content: String) {
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = chatRepository.createConversation(userId)
                if (response.isSuccess && response.data != null) {
                    val newConversation = response.data
                    _uiState.value = _uiState.value.copy(
                        conversations = listOf(newConversation) + _uiState.value.conversations,
                        currentConversation = newConversation,
                        messages = emptyList(),
                        isLoading = false
                    )
                    sendMessage(content)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "创建会话失败：${e.message}"
                )
            }
        }
    }

    fun selectConversation(conversation: Conversation) {
        // 切换会话时取消正在进行的流式请求
        cancelStream()
        _uiState.value = _uiState.value.copy(
            currentConversation = conversation,
            messages = emptyList()
        )
        loadMessages(conversation.id)
    }

    fun loadMessages(conversationId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = chatRepository.getMessages(conversationId)
                if (response.isSuccess && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        messages = response.data,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载消息失败：${e.message}"
                )
            }
        }
    }

    /**
     * 发送消息 - 使用 SSE 流式输出，自动重试，失败回退到普通请求
     */
    fun sendMessage(content: String) {
        val conversationId = _uiState.value.currentConversation?.id ?: return
        val isFirstMessage = _uiState.value.messages.isEmpty()
        val initialTitle = _uiState.value.currentConversation?.title

        // 先添加用户消息到列表
        val userMessage = Message(
            id = System.currentTimeMillis(),
            conversationId = conversationId,
            role = "user",
            content = content
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isSending = true,
            isStreaming = true,
            streamingContent = ""
        )

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            val username = tokenManager.getUsername()

            streamWithRetry(userId, conversationId, content, username, retryCount = 0)

            // 流结束后刷新会话标题
            if (isFirstMessage) {
                for (delay in listOf(3000L, 6000L, 9000L, 12000L)) {
                    kotlinx.coroutines.delay(delay)
                    loadConversations()
                    val updated = _uiState.value.conversations.find { it.id == conversationId }
                    if (updated != null && updated.title != null && updated.title != initialTitle) {
                        _uiState.value = _uiState.value.copy(currentConversation = updated)
                        break
                    }
                }
            }
        }
    }

    /**
     * 带重试的流式发送
     */
    private suspend fun streamWithRetry(
        userId: Long,
        conversationId: Long,
        content: String,
        username: String?,
        retryCount: Int
    ) {
        var accumulatedContent = ""
        var hasError = false
        var errorMsg = ""
        var productCards: List<Product>? = null

        // 获取用户画像
        val gender = tokenManager.getGender()
        val skinType = tokenManager.getSkinType()
        val preferenceTags = tokenManager.getPreferenceTags()

        chatRepository.streamMessage(userId, conversationId, content, username, gender, skinType, preferenceTags)
            .catch { e ->
                Log.e(TAG, "Stream error (attempt ${retryCount + 1}): ${e.message}", e)
                hasError = true
                errorMsg = e.message ?: "连接异常"
            }
            .collect { event ->
                when (event.type) {
                    "token", "answer" -> {
                        accumulatedContent += event.content
                        _uiState.value = _uiState.value.copy(
                            streamingContent = accumulatedContent
                        )
                    }
                    "product_cards" -> {
                        productCards = parseProductCards(event.productCards)
                        Log.d(TAG, "Parsed product cards: ${productCards?.size ?: 0} items")
                    }
                    "error" -> {
                        hasError = true
                        errorMsg = event.content
                    }
                    "end" -> {
                        // 流正常结束
                    }
                }
            }

        // 流结束处理
        when {
            // 有错误且还有重试次数 -> 重试
            hasError && retryCount < MAX_RETRY_COUNT -> {
                Log.d(TAG, "Retrying stream (attempt ${retryCount + 2})")
                streamWithRetry(userId, conversationId, content, username, retryCount + 1)
            }
            // 有错误、无内容、已用完重试次数 -> 回退到普通请求
            hasError && accumulatedContent.isBlank() && retryCount >= MAX_RETRY_COUNT -> {
                Log.d(TAG, "Stream failed after retries, falling back to HTTP")
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    streamingContent = ""
                )
                fallbackSendMessage(userId, conversationId, content)
            }
            // 有内容（不管有没有错误）-> 保存已收到的内容
            accumulatedContent.isNotBlank() -> {
                Log.d(TAG, "Stream ended. content length=${accumulatedContent.length}, productCards=${productCards?.size ?: 0}")
                val aiMessage = Message(
                    id = System.currentTimeMillis() + 1,
                    conversationId = conversationId,
                    role = "assistant",
                    content = accumulatedContent,
                    productCards = productCards
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isSending = false,
                    isStreaming = false,
                    streamingContent = "",
                    errorMessage = if (hasError) errorMsg else null
                )
            }
            // 无内容无错误（异常情况）-> 清理状态
            else -> {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    isStreaming = false,
                    streamingContent = ""
                )
            }
        }
    }

    /**
     * 回退到普通 HTTP 请求
     */
    private suspend fun fallbackSendMessage(userId: Long, conversationId: Long, content: String) {
        try {
            val response = chatRepository.sendMessage(userId, conversationId, content)
            if (response.isSuccess && response.data != null) {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + response.data,
                    isSending = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = response.message
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSending = false,
                errorMessage = "发送失败：${e.message}"
            )
        }
    }

    /**
     * 取消正在进行的流式请求
     */
    fun cancelStream() {
        streamJob?.cancel()
        streamJob = null

        val currentState = _uiState.value
        if (currentState.isStreaming && currentState.streamingContent.isNotBlank()) {
            // 保存已收到的部分内容
            val partialMessage = Message(
                id = System.currentTimeMillis(),
                conversationId = currentState.currentConversation?.id ?: 0,
                role = "assistant",
                content = currentState.streamingContent
            )
            _uiState.value = currentState.copy(
                messages = currentState.messages + partialMessage,
                isSending = false,
                isStreaming = false,
                streamingContent = ""
            )
        } else {
            _uiState.value = currentState.copy(
                isSending = false,
                isStreaming = false,
                streamingContent = ""
            )
        }
    }

    /**
     * 提交消息反馈（赞/踩）
     */
    fun submitFeedback(messageId: Long, feedbackType: Int) {
        viewModelScope.launch {
            try {
                val response = chatRepository.submitFeedback(messageId, feedbackType)
                if (response.isSuccess && response.data != null) {
                    // 更新本地消息的反馈状态
                    val updatedMessages = _uiState.value.messages.map { msg ->
                        if (msg.id == messageId) msg.copy(feedbackType = feedbackType) else msg
                    }
                    _uiState.value = _uiState.value.copy(messages = updatedMessages)
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "反馈失败")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "反馈失败：${e.message}"
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseProductCards(raw: Any?): List<Product>? {
        if (raw == null) return null
        return try {
            // raw 可能是 JSONArray、String 或其他类型，统一转为 JSON 字符串
            val json = when (raw) {
                is String -> raw
                is org.json.JSONArray -> raw.toString()
                else -> raw.toString()
            }
            val type = object : TypeToken<List<Product>>() {}.type
            val cards: List<Product> = gson.fromJson(json, type)
            Log.d(TAG, "parseProductCards: ${cards.size} items")
            cards
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse product cards: ${e.message}", e)
            null
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            try {
                val response = chatRepository.deleteConversation(conversationId)
                if (response.isSuccess) {
                    val updatedList = _uiState.value.conversations.filter { it.id != conversationId }
                    val newCurrent = if (_uiState.value.currentConversation?.id == conversationId) {
                        null
                    } else {
                        _uiState.value.currentConversation
                    }
                    _uiState.value = _uiState.value.copy(
                        conversations = updatedList,
                        currentConversation = newCurrent,
                        messages = if (newCurrent == null) emptyList() else _uiState.value.messages
                    )
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = response.message)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "删除失败：${e.message}"
                )
            }
        }
    }

    fun clearCurrentConversation() {
        cancelStream()
        _uiState.value = _uiState.value.copy(
            currentConversation = null,
            messages = emptyList()
        )
    }

    fun pinConversation(conversationId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            try {
                val conversation = _uiState.value.conversations.find { it.id == conversationId } ?: return@launch
                val updated = conversation.copy(isPinned = isPinned)
                val response = chatRepository.updateConversation(conversationId, updated)
                if (response.isSuccess) {
                    loadConversations()
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = response.message)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "操作失败：${e.message}")
            }
        }
    }

    fun renameConversation(conversationId: Long, newTitle: String) {
        viewModelScope.launch {
            try {
                val conversation = _uiState.value.conversations.find { it.id == conversationId } ?: return@launch
                val updated = conversation.copy(title = newTitle)
                val response = chatRepository.updateConversation(conversationId, updated)
                if (response.isSuccess) {
                    loadConversations()
                    if (_uiState.value.currentConversation?.id == conversationId) {
                        _uiState.value = _uiState.value.copy(
                            currentConversation = _uiState.value.currentConversation?.copy(title = newTitle)
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = response.message)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "重命名失败：${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearState() {
        cancelStream()
        _uiState.value = ChatUiState()
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}
