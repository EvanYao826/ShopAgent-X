package com.evanyao.shopagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.TokenManager
import com.evanyao.shopagent.data.model.LoginRequest
import com.evanyao.shopagent.data.model.ProfileUpdateRequest
import com.evanyao.shopagent.data.model.RegisterRequest
import com.evanyao.shopagent.data.network.api.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isProfileSetupDone: Boolean = false,
    val username: String? = null,
    val phone: String? = null,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        checkLoginState()
    }

    private fun checkLoginState() {
        viewModelScope.launch {
            val token = tokenManager.getToken()
            if (token != null) {
                val profileCompleted = tokenManager.isProfileCompleted()
                val username = tokenManager.getUsername()
                val phone = tokenManager.getPhone()
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = true,
                    isProfileSetupDone = profileCompleted,
                    username = username,
                    phone = phone
                )
            }
        }
    }

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = authApi.login(LoginRequest(phone, password))
                if (response.isSuccess && response.data != null) {
                    val token = response.data["accessToken"] as? String
                    val refreshToken = response.data["refreshToken"] as? String
                    val userMap = response.data["user"] as? Map<*, *>
                    val userId = (userMap?.get("id") as? Number)?.toLong()
                    val preferenceTags = userMap?.get("preferenceTags") as? List<*>
                    val skinType = userMap?.get("skinType") as? String
                    if (token != null) {
                        tokenManager.saveToken(token)
                        if (refreshToken != null) {
                            tokenManager.saveRefreshToken(refreshToken)
                        }
                        if (userId != null) {
                            tokenManager.saveUserId(userId)
                        }
                        val username = userMap?.get("username") as? String
                        if (username != null) {
                            tokenManager.saveUsername(username)
                        }
                        tokenManager.savePhone(phone)
                        if (skinType != null) {
                            tokenManager.saveSkinType(skinType)
                        }
                        if (!preferenceTags.isNullOrEmpty()) {
                            tokenManager.savePreferenceTags(preferenceTags.map { it.toString() })
                        }
                        // 判断是否已完成资料设置：preferenceTags 不为空
                        val profileDone = !preferenceTags.isNullOrEmpty()
                        if (profileDone) {
                            tokenManager.setProfileCompleted()
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            isProfileSetupDone = profileDone,
                            username = username,
                            phone = phone
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "登录失败：未获取到Token"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "网络错误：${e.message}"
                )
            }
        }
    }

    fun register(phone: String, code: String, password: String, username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = authApi.register(RegisterRequest(phone, code, password, username))
                if (response.isSuccess && response.data != null) {
                    val token = response.data["accessToken"] as? String
                    val refreshToken = response.data["refreshToken"] as? String
                    val userMap = response.data["user"] as? Map<*, *>
                    val userId = (userMap?.get("id") as? Number)?.toLong()
                    if (token != null) {
                        tokenManager.saveToken(token)
                        if (refreshToken != null) {
                            tokenManager.saveRefreshToken(refreshToken)
                        }
                        if (userId != null) {
                            tokenManager.saveUserId(userId)
                        }
                        // 新注册用户，引导页未完成
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            isProfileSetupDone = false
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "网络错误：${e.message}"
                )
            }
        }
    }

    fun sendCode(phone: String) {
        viewModelScope.launch {
            try {
                authApi.sendCode(phone)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "发送验证码失败：${e.message}"
                )
            }
        }
    }

    fun saveProfileSetup(gender: Int, ageRange: String, skinType: String, tags: List<String>) {
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId() ?: return@launch
                val request = ProfileUpdateRequest(
                    userId = userId,
                    gender = gender,
                    ageRange = ageRange,
                    skinType = skinType,
                    preferenceTags = tags
                )
                val response = authApi.updateUserInfo(request)
                if (response.isSuccess) {
                    tokenManager.setProfileCompleted()
                    tokenManager.saveSkinType(skinType)
                    tokenManager.savePreferenceTags(tags)
                    _uiState.value = _uiState.value.copy(isProfileSetupDone = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "保存失败：${response.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "网络错误：${e.message}"
                )
            }
        }
    }

    fun skipProfileSetup() {
        // 跳过不保存，下次登录还会弹出引导页
        _uiState.value = _uiState.value.copy(isProfileSetupDone = true)
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearAll()
            _uiState.value = AuthUiState(isLoggedIn = false)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
