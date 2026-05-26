package com.evanyao.shopagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.TokenManager
import com.evanyao.shopagent.data.model.Conversation
import com.evanyao.shopagent.data.model.Message
import com.evanyao.shopagent.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
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

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

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
                    // 同步更新当前会话（标题可能已由 AI 生成）
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

        // 根据肤质推荐
        when (skinType) {
            "油性" -> recs.add("推荐一款适合油皮的控油精华")
            "干性" -> recs.add("推荐一款高保湿面霜，干皮救星")
            "混合型" -> recs.add("推荐适合混合肌的水乳套装")
            "敏感型" -> recs.add("敏感肌可以用什么温和面膜？")
            "中性" -> recs.add("推荐一款日常基础护肤套装")
        }

        // 根据偏好标签推荐（去重：已有相似内容则跳过）
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

        // 不足4个时用默认补充（与已有推荐去重）
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

    fun sendMessage(content: String) {
        val conversationId = _uiState.value.currentConversation?.id ?: return
        val isFirstMessage = _uiState.value.messages.isEmpty()
        val initialTitle = _uiState.value.currentConversation?.title

        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                // 先添加用户消息到列表
                val userMessage = Message(
                    id = System.currentTimeMillis(),
                    conversationId = conversationId,
                    role = "user",
                    content = content
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + userMessage
                )

                // 发送到后端
                val response = chatRepository.sendMessage(userId, conversationId, content)
                if (response.isSuccess && response.data != null) {
                    // 添加 AI 回复到列表
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + response.data,
                        isSending = false
                    )

                    // 第一条消息发送后，轮询刷新会话列表以获取 AI 生成的标题
                    if (isFirstMessage) {
                        for (delay in listOf(3000L, 6000L, 9000L, 12000L)) {
                            kotlinx.coroutines.delay(delay)
                            loadConversations()
                            // 标题与创建时不同，说明已被 AI 更新
                            val updated = _uiState.value.conversations.find { it.id == conversationId }
                            if (updated != null && updated.title != null && updated.title != initialTitle) {
                                _uiState.value = _uiState.value.copy(currentConversation = updated)
                                break
                            }
                        }
                    }
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
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            try {
                val response = chatRepository.deleteConversation(conversationId)
                if (response.isSuccess) {
                    // 从列表中移除
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
                    // 如果重命名的是当前会话，也更新当前会话
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
        _uiState.value = ChatUiState()
    }

    fun refreshOnLogin() {
        _uiState.value = ChatUiState()
        loadConversations()
        loadRecommendations()
    }
}
