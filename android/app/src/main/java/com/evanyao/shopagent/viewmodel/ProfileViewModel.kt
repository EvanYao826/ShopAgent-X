package com.evanyao.shopagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.TokenManager
import com.evanyao.shopagent.data.model.ProfileUpdateRequest
import com.evanyao.shopagent.data.model.User
import com.evanyao.shopagent.data.network.api.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val errorMessage: String? = null,
    val updateSuccess: Boolean = false
)

class ProfileViewModel(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = authApi.getProfile()
                if (response.isSuccess && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        user = response.data
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
                    errorMessage = "加载失败：${e.message}"
                )
            }
        }
    }

    fun updateProfile(
        username: String?,
        gender: Int?,
        ageRange: String?,
        skinType: String?,
        preferenceTags: List<String>?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, updateSuccess = false)
            try {
                val currentUser = _uiState.value.user
                val request = ProfileUpdateRequest(
                    username = username ?: currentUser?.username,
                    gender = gender ?: currentUser?.gender,
                    ageRange = ageRange ?: currentUser?.ageRange,
                    skinType = skinType ?: currentUser?.skinType,
                    preferenceTags = preferenceTags ?: currentUser?.preferenceTags
                )
                val response = authApi.updateUserInfo(request)
                if (response.isSuccess && response.data != null) {
                    // Update local cache
                    if (username != null) {
                        tokenManager.saveUsername(username)
                    }
                    if (skinType != null) {
                        tokenManager.saveSkinType(skinType)
                    }
                    if (preferenceTags != null) {
                        tokenManager.savePreferenceTags(preferenceTags)
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        user = response.data,
                        updateSuccess = true
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
                    errorMessage = "保存失败：${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearUpdateSuccess() {
        _uiState.value = _uiState.value.copy(updateSuccess = false)
    }
}
