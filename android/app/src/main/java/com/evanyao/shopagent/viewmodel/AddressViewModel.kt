package com.evanyao.shopagent.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evanyao.shopagent.data.model.AddressRequest
import com.evanyao.shopagent.data.repository.AddressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AddressItem(
    val id: Long,
    val receiverName: String,
    val phone: String,
    val province: String,
    val city: String,
    val district: String,
    val detail: String,
    val isDefault: Boolean
)

data class AddressUiState(
    val isLoading: Boolean = false,
    val addressList: List<AddressItem> = emptyList(),
    val errorMessage: String? = null,
    val operationSuccess: Boolean = false
)

class AddressViewModel(
    private val addressRepository: AddressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddressUiState())
    val uiState: StateFlow<AddressUiState> = _uiState

    fun loadAddressList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = addressRepository.list()
                if (response.isSuccess && response.data != null) {
                    val list = response.data.map { item ->
                        AddressItem(
                            id = item.id,
                            receiverName = item.receiverName,
                            phone = item.phone,
                            province = item.province,
                            city = item.city,
                            district = item.district,
                            detail = item.detail,
                            isDefault = item.isDefault == 1
                        )
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, addressList = list)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = response.message)
                }
            } catch (e: Exception) {
                Log.e("AddressVM", "Load address list failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "加载失败: ${e.message}")
            }
        }
    }

    fun addAddress(
        receiverName: String, phone: String,
        province: String, city: String, district: String, detail: String,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            try {
                val response = addressRepository.add(AddressRequest(
                    receiverName = receiverName,
                    phone = phone,
                    province = province,
                    city = city,
                    district = district,
                    detail = detail,
                    isDefault = if (isDefault) 1 else 0
                ))
                if (response.isSuccess) {
                    _uiState.value = _uiState.value.copy(operationSuccess = true)
                    loadAddressList()
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = response.message)
                }
            } catch (e: Exception) {
                Log.e("AddressVM", "Add address failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "添加失败: ${e.message}")
            }
        }
    }

    fun updateAddress(
        id: Long, receiverName: String, phone: String,
        province: String, city: String, district: String, detail: String,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            try {
                val response = addressRepository.update(AddressRequest(
                    id = id,
                    receiverName = receiverName,
                    phone = phone,
                    province = province,
                    city = city,
                    district = district,
                    detail = detail,
                    isDefault = if (isDefault) 1 else 0
                ))
                if (response.isSuccess) {
                    _uiState.value = _uiState.value.copy(operationSuccess = true)
                    loadAddressList()
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = response.message)
                }
            } catch (e: Exception) {
                Log.e("AddressVM", "Update address failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "修改失败: ${e.message}")
            }
        }
    }

    fun deleteAddress(id: Long) {
        viewModelScope.launch {
            try {
                addressRepository.delete(id)
                _uiState.value = _uiState.value.copy(
                    addressList = _uiState.value.addressList.filter { it.id != id }
                )
            } catch (e: Exception) {
                Log.e("AddressVM", "Delete address failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "删除失败: ${e.message}")
            }
        }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch {
            try {
                addressRepository.setDefault(id)
                _uiState.value = _uiState.value.copy(
                    addressList = _uiState.value.addressList.map { item ->
                        item.copy(isDefault = item.id == id)
                    }
                )
            } catch (e: Exception) {
                Log.e("AddressVM", "Set default failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "设置失败: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearOperationSuccess() {
        _uiState.value = _uiState.value.copy(operationSuccess = false)
    }

    fun clearState() {
        _uiState.value = AddressUiState()
    }
}
