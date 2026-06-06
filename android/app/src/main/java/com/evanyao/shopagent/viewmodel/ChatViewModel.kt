package com.evanyao.shopagent.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.TokenManager
import com.evanyao.shopagent.data.model.CartSelection
import com.evanyao.shopagent.data.model.ConfirmButton
import com.evanyao.shopagent.data.model.ConfirmCard
import com.evanyao.shopagent.data.model.Conversation
import com.evanyao.shopagent.data.model.Message
import com.evanyao.shopagent.data.model.Product
import com.evanyao.shopagent.data.model.ProductSku
import com.evanyao.shopagent.data.repository.CartRepository
import com.evanyao.shopagent.data.repository.ChatRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 输入模式 */
enum class InputMode { TEXT, VOICE }

/** 聊天页面 UI 状态 */
data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),      // 会话列表
    val currentConversation: Conversation? = null,            // 当前选中的会话
    val messages: List<Message> = emptyList(),                // 当前会话的消息列表
    val isLoading: Boolean = false,                           // 是否正在加载
    val isSending: Boolean = false,                           // 是否正在发送消息
    val isStreaming: Boolean = false,                         // 是否正在流式接收
    val streamingContent: String = "",                        // 流式接收的临时内容
    val errorMessage: String? = null,                         // 错误提示
    val userGender: Int? = null,                              // 用户性别（用于推荐问题）
    val inputMode: InputMode = InputMode.TEXT,                // 输入模式（文字/语音）
    val isRecording: Boolean = false,                         // 是否正在录音
    val pendingVoiceText: String? = null,                     // 语音识别结果（等待用户确认）
    val recommendations: List<String> = listOf(               // 推荐问题列表
        "推荐一款适合油皮的精华",
        "敏感肌可以用什么面膜？",
        "有没有好用的防晒霜？",
        "抗衰老护肤品推荐"
    )
)

/** 聊天 ViewModel，管理会话列表、消息收发、SSE 流式输出 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val tokenManager: TokenManager,
    private val cartRepository: CartRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MAX_RETRY_COUNT = 2
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    // 购物车操作事件流，用于通知外部刷新购物车
    private val _cartEvent = MutableSharedFlow<Unit>()
    val cartEvent: SharedFlow<Unit> = _cartEvent

    // 加入购物车时选择规格的状态
    private val _skuSelectionProduct = MutableStateFlow<Product?>(null)
    val skuSelectionProduct: StateFlow<Product?> = _skuSelectionProduct
    private val _skuSelectionList = MutableStateFlow<List<ProductSku>>(emptyList())
    val skuSelectionList: StateFlow<List<ProductSku>> = _skuSelectionList
    private val _pendingAddCartProductIds = mutableListOf<Long>()
    private var _addedCount = 0      // 本轮已加入购物车的商品数量
    private var _totalToAdd = 0      // 本轮需要加入购物车的商品总数

    private var streamJob: Job? = null
    private val gson = Gson()

    init {
        loadConversations()
        loadRecommendations()
        loadUserGender()
    }

    private fun loadUserGender() {
        viewModelScope.launch {
            val genderStr = tokenManager.getGender()
            val genderInt = when (genderStr) {
                "男" -> 1
                "女" -> 2
                else -> 0
            }
            _uiState.value = _uiState.value.copy(userGender = genderInt)
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
            val gender = tokenManager.getGender()
            val recs = generateRecommendations(skinType, tags, gender)
            _uiState.value = _uiState.value.copy(recommendations = recs)
        }
    }

    private fun generateRecommendations(skinType: String?, tags: List<String>, gender: String?): List<String> {
        val recs = mutableListOf<String>()
        val month = java.time.LocalDate.now().monthValue

        // 肤质适配
        when (skinType) {
            "油性" -> recs.add("推荐一款适合油皮的控油精华")
            "干性" -> recs.add("推荐一款高保湿面霜，干皮救星")
            "混合型" -> recs.add("推荐适合混合肌的水乳套装")
            "敏感型" -> recs.add("敏感肌可以用什么温和面膜？")
            "中性" -> recs.add("推荐一款日常基础护肤套装")
        }

        // 性别适配
        when (gender) {
            "男" -> recs.add("男士护肤套装推荐")
            "女" -> recs.add("适合女生的平价好物推荐")
        }

        // 季节适配
        val seasonRec = when (month) {
            in 3..5 -> "春季敏感肌修复面膜推荐"
            in 6..8 -> "夏季清爽防晒霜推荐"
            in 9..11 -> "秋季保湿精华推荐"
            else -> "冬季滋润身体乳推荐"
        }
        recs.add(seasonRec)

        // 偏好标签适配
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

        // 默认推荐兜底
        val defaults = listOf(
            "抗衰老护肤品推荐",
            "有没有平价好用的水乳推荐？",
            "适合学生党的护肤套装"
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

    fun createConversationAndSendMessage(content: String, imageUri: Uri? = null) {
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
                    sendMessage(content, imageUri = imageUri?.toString())
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
     * @param silent 为 true 时不将用户消息添加到 UI（用于内部消息）
     */
    fun sendMessage(content: String, silent: Boolean = false, imageUri: String? = null) {
        val conversationId = _uiState.value.currentConversation?.id ?: return
        val isFirstMessage = _uiState.value.messages.isEmpty()
        val initialTitle = _uiState.value.currentConversation?.title

        if (!silent) {
            // 添加用户消息到列表
            val userMessage = Message(
                id = System.currentTimeMillis(),
                conversationId = conversationId,
                role = "user",
                content = content,
                imageUri = imageUri
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + userMessage
            )
        }
        _uiState.value = _uiState.value.copy(
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
        var confirmCard: ConfirmCard? = null
        var cartSelection: CartSelection? = null
        var cartSelectionType: String? = null  // "cart_selection" 或 "cart_list"
        var taskType: String? = null

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
                    "confirm_card" -> {
                        confirmCard = parseConfirmCard(event.confirmCard)
                        Log.d(TAG, "Parsed confirm card: $confirmCard")
                    }
                    "cart_selection", "cart_list" -> {
                        cartSelection = parseCartSelection(event.cartSelection)
                        cartSelectionType = event.type
                        Log.d(TAG, "Parsed cart ${event.type}: $cartSelection")
                    }
                    "routed" -> {
                        taskType = event.taskType
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
            // 有购物车选择卡片 -> 显示可勾选商品列表
            cartSelection != null -> {
                Log.d(TAG, "Stream ended with ${cartSelectionType ?: "cart_selection"}")
                markLastConfirmCardAnswered()
                val aiMessage = Message(
                    id = System.currentTimeMillis() + 1,
                    conversationId = conversationId,
                    role = "assistant",
                    content = cartSelection!!.message,
                    messageType = cartSelectionType ?: "cart_selection",
                    cartSelection = cartSelection
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isSending = false,
                    isStreaming = false,
                    streamingContent = ""
                )
            }
            // 有确认卡片 -> 显示确认卡片消息
            confirmCard != null -> {
                Log.d(TAG, "Stream ended with confirm card")
                // 先标记之前的确认卡片为已回答
                markLastConfirmCardAnswered()
                val aiMessage = Message(
                    id = System.currentTimeMillis() + 1,
                    conversationId = conversationId,
                    role = "assistant",
                    content = confirmCard!!.message,
                    messageType = "confirm_card",
                    confirmCard = confirmCard
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isSending = false,
                    isStreaming = false,
                    streamingContent = ""
                )
            }
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
                Log.d(TAG, "Stream ended. content length=${accumulatedContent.length}, productCards=${productCards?.size ?: 0}, taskType=$taskType")
                markLastConfirmCardAnswered()
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
                // 购物车操作完成后触发刷新事件
                if (taskType == "cart") {
                    viewModelScope.launch { _cartEvent.emit(Unit) }
                }
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
        if (currentState.isStreaming) {
            val content = if (currentState.streamingContent.isNotBlank()) {
                currentState.streamingContent + "\n\n（已停止生成）"
            } else {
                "已停止生成"
            }
            val partialMessage = Message(
                id = System.currentTimeMillis(),
                conversationId = currentState.currentConversation?.id ?: 0,
                role = "assistant",
                content = content
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

    private fun parseConfirmCard(raw: Any?): ConfirmCard? {
        if (raw == null) return null
        return try {
            val json = when (raw) {
                is String -> raw
                is org.json.JSONObject -> raw.toString()
                else -> raw.toString()
            }
            val jsonObj = org.json.JSONObject(json)
            val message = jsonObj.optString("message", "请确认操作")
            val action = jsonObj.optString("action", "")

            // 解析单个商品信息（兼容旧格式）
            val product = if (jsonObj.has("product") && !jsonObj.isNull("product")) {
                val productJson = jsonObj.getJSONObject("product")
                gson.fromJson(productJson.toString(), Product::class.java)
            } else null

            // 解析商品列表（批量删除用）
            val products = if (jsonObj.has("products") && !jsonObj.isNull("products")) {
                val arr = jsonObj.getJSONArray("products")
                (0 until arr.length()).map { i ->
                    gson.fromJson(arr.getJSONObject(i).toString(), Product::class.java)
                }
            } else null

            // 解析按钮
            val buttons = mutableListOf<ConfirmButton>()
            if (jsonObj.has("buttons")) {
                val buttonsArray = jsonObj.getJSONArray("buttons")
                for (i in 0 until buttonsArray.length()) {
                    val btnObj = buttonsArray.getJSONObject(i)
                    buttons.add(ConfirmButton(
                        type = btnObj.optString("type", ""),
                        label = btnObj.optString("label", "")
                    ))
                }
            }

            ConfirmCard(
                message = message,
                action = action,
                product = product,
                products = products,
                buttons = buttons
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse confirm card: ${e.message}", e)
            null
        }
    }

    private fun parseCartSelection(raw: Any?): CartSelection? {
        if (raw == null) return null
        return try {
            val json = when (raw) {
                is String -> raw
                is org.json.JSONObject -> raw.toString()
                else -> raw.toString()
            }
            val jsonObj = org.json.JSONObject(json)
            val message = jsonObj.optString("message", "请选择要加入购物车的商品：")
            val items = mutableListOf<Product>()
            if (jsonObj.has("items")) {
                val itemsArray = jsonObj.getJSONArray("items")
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val product = gson.fromJson(itemObj.toString(), Product::class.java)
                    items.add(product)
                }
            }
            CartSelection(message = message, items = items)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cart selection: ${e.message}", e)
            null
        }
    }

    /**
     * 发送确认卡片的按钮点击响应（确认/取消）
     */
    fun sendConfirmAction(action: String) {
        // 立即标记确认卡片为已回答，防止重复点击
        markLastConfirmCardAnswered()
        val label = if (action == "confirm") "确认" else "取消"
        sendMessage(label)
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

    /** 开始加入购物车流程：逐个弹出规格选择 */
    fun startAddToCartWithSku(productIds: List<Long>) {
        if (productIds.isEmpty()) return
        _pendingAddCartProductIds.clear()
        _pendingAddCartProductIds.addAll(productIds)
        _addedCount = 0
        _totalToAdd = productIds.size
        showNextSkuSelection()
    }

    private fun showNextSkuSelection() {
        if (_pendingAddCartProductIds.isEmpty()) {
            _skuSelectionProduct.value = null
            _skuSelectionList.value = emptyList()
            return
        }
        val productId = _pendingAddCartProductIds.first()
        val product = findProductInMessages(productId)
        if (product == null) {
            _pendingAddCartProductIds.removeFirst()
            cartRepository?.let { repo ->
                viewModelScope.launch {
                    try {
                        repo.addItem(productId)
                        _addedCount++
                    } catch (_: Exception) {}
                    _cartEvent.emit(Unit)
                    checkAndSendSummary()
                }
            }
            showNextSkuSelection()
            return
        }
        cartRepository?.let { repo ->
            viewModelScope.launch {
                try {
                    val response = repo.getProductSkus(productId)
                    if (response.isSuccess && response.data != null && response.data.size > 1) {
                        _skuSelectionProduct.value = product
                        _skuSelectionList.value = response.data
                    } else {
                        val skuId = response.data?.firstOrNull()?.id
                        repo.addItem(productId, skuId)
                        _addedCount++
                        _cartEvent.emit(Unit)
                        _pendingAddCartProductIds.removeFirst()
                        checkAndSendSummary()
                        showNextSkuSelection()
                    }
                } catch (e: Exception) {
                    try { repo.addItem(productId) } catch (_: Exception) {}
                    _cartEvent.emit(Unit)
                    _pendingAddCartProductIds.removeFirst()
                    checkAndSendSummary()
                    showNextSkuSelection()
                }
            }
        }
    }

    /** 确认当前商品的规格选择 */
    fun confirmSkuSelection(skuId: Long) {
        val product = _skuSelectionProduct.value ?: return
        _skuSelectionProduct.value = null
        _skuSelectionList.value = emptyList()
        _pendingAddCartProductIds.remove(product.id)

        cartRepository?.let { repo ->
            viewModelScope.launch {
                try {
                    repo.addItem(product.id, skuId)
                    _addedCount++
                    _cartEvent.emit(Unit)
                    checkAndSendSummary()
                } catch (e: Exception) {
                    Log.e(TAG, "confirmSkuSelection addItem error: ${e.message}", e)
                }
            }
        }
        if (_pendingAddCartProductIds.isNotEmpty()) {
            showNextSkuSelection()
        }
    }

    /** 取消当前商品的规格选择，跳到下一个 */
    fun cancelSkuSelection() {
        val product = _skuSelectionProduct.value
        if (product != null) {
            _pendingAddCartProductIds.remove(product.id)
            if (_totalToAdd > 0) _totalToAdd--
        }
        _skuSelectionProduct.value = null
        _skuSelectionList.value = emptyList()
        if (_pendingAddCartProductIds.isNotEmpty()) {
            showNextSkuSelection()
        } else {
            checkAndSendSummary()
        }
    }

    /** 检查是否全部加购完成，通过 SSE 流发送汇总消息（自动持久化） */
    private fun checkAndSendSummary() {
        if (_addedCount >= _totalToAdd && _addedCount > 0) {
            val count = _addedCount
            _addedCount = 0
            _totalToAdd = 0
            // 走 SSE 流，Python 返回 AI 消息，Java 自动保存（跟删除一样）
            sendMessage("ADD_CONFIRM:$count", silent = true)
        }
    }

    /** 标记最近一条确认卡片消息为已回答（按钮变灰） */
    private fun markLastConfirmCardAnswered() {
        val messages = _uiState.value.messages.toMutableList()
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            if (msg.messageType == "confirm_card" && msg.confirmCard != null && !msg.confirmCard.answered) {
                messages[i] = msg.copy(confirmCard = msg.confirmCard.copy(answered = true))
                _uiState.value = _uiState.value.copy(messages = messages)
                return
            }
        }
    }

    private fun findProductInMessages(productId: Long): Product? {
        for (msg in _uiState.value.messages) {
            msg.productCards?.find { it.id == productId }?.let { return it }
            msg.cartSelection?.items?.find { it.id == productId }?.let { return it }
        }
        return null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /** 切换输入模式（文字/语音） */
    fun toggleInputMode() {
        val newMode = if (_uiState.value.inputMode == InputMode.TEXT) {
            InputMode.VOICE
        } else {
            InputMode.TEXT
        }
        _uiState.value = _uiState.value.copy(inputMode = newMode)
    }

    /** 开始录音 */
    fun startRecording() {
        _uiState.value = _uiState.value.copy(isRecording = true)
    }

    /** 停止录音 */
    fun stopRecording() {
        _uiState.value = _uiState.value.copy(isRecording = false)
    }

    /** 语音识别完成，将结果填入输入框等待用户确认 */
    fun sendVoiceResult(text: String) {
        if (text.isNotBlank() && text != "语音识别未配置" && !text.startsWith("语音识别失败")) {
            // 检查是否为无效语音（无实际说话内容）
            val noSpeechKeywords = listOf("没有可识别", "未包含", "无人类", "嗡鸣", "底噪", "噪音", "未识别到", "未能识别")
            if (noSpeechKeywords.any { text.contains(it) }) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = "未检测到说话内容，请再试一次"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    pendingVoiceText = text,
                    isSending = false
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(isSending = false, errorMessage = text)
        }
    }

    /** 清除待确认的语音文本（已填入输入框后调用） */
    fun clearPendingVoiceText() {
        _uiState.value = _uiState.value.copy(pendingVoiceText = null)
    }

    /** 发送图片识别结果（用于对话页拍照） */
    fun sendPhotoResult(imageDescription: String, imageUri: Uri? = null) {
        if (imageDescription.isNotBlank() && !imageDescription.startsWith("无法识别")) {
            // 用户看到的是简洁消息，图片描述作为搜索依据传给后端
            val question = "根据图片给我推荐相似商品\n\n图片内容：$imageDescription"
            val uriString = imageUri?.toString()
            if (_uiState.value.currentConversation == null) {
                createConversationAndSendMessage(question, imageUri)
            } else {
                sendMessage(question, imageUri = uriString)
            }
        } else {
            _uiState.value = _uiState.value.copy(isSending = false, errorMessage = "图片识别失败，请重试")
        }
    }

    /** 从 URI 发送图片（拍照或相册选择） */
    fun sendPhotoFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSending = true)

                // 读取图片并构建请求
                val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

                if (imageBytes == null) {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        errorMessage = "无法读取图片"
                    )
                    return@launch
                }

                val requestBody = imageBytes.toRequestBody("image/*".toMediaType())
                val part = MultipartBody.Part.createFormData("file", "photo.jpg", requestBody)

                // 调用图片识别接口
                val response = chatRepository.recognizeImage(part)
                if (response.isSuccess && response.data != null) {
                    val recognizedText = response.data
                    Log.d(TAG, "Image recognized: $recognizedText")

                    // 将识别结果发送给 AI 推荐商品，传入图片URI用于聊天气泡显示
                    sendPhotoResult(recognizedText, imageUri = uri)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        errorMessage = response.message ?: "图片识别失败"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send photo failed", e)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = "图片识别失败: ${e.message}"
                )
            }
        }
    }

    /** 发送语音文件进行识别 */
    fun sendVoiceFile(audioFile: java.io.File) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSending = true)

                val audioBytes = audioFile.readBytes()
                val requestBody = audioBytes.toRequestBody("audio/*".toMediaType())
                val part = MultipartBody.Part.createFormData("file", audioFile.name, requestBody)

                val response = chatRepository.recognizeVoice(part)
                if (response.isSuccess && response.data != null) {
                    val recognizedText = response.data
                    Log.d(TAG, "Voice recognized: $recognizedText")
                    sendVoiceResult(recognizedText)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        errorMessage = response.message ?: "语音识别失败"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send voice failed", e)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = "语音识别失败: ${e.message}"
                )
            } finally {
                audioFile.delete()
            }
        }
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
