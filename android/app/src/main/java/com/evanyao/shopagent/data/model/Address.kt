package com.evanyao.shopagent.data.model

import com.google.gson.annotations.SerializedName

data class AddressRequest(
    val id: Long? = null,
    val receiverName: String,
    val phone: String,
    val province: String,
    val city: String,
    val district: String,
    val detail: String,
    @SerializedName("isDefault")
    val isDefault: Int = 0
)

data class AddressResponse(
    val id: Long,
    val receiverName: String,
    val phone: String,
    val province: String,
    val city: String,
    val district: String,
    val detail: String,
    @SerializedName("isDefault")
    val isDefault: Int = 0
)
