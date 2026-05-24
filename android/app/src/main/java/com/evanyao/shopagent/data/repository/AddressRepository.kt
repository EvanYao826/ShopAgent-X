package com.evanyao.shopagent.data.repository

import com.evanyao.shopagent.data.network.api.AddressApi

class AddressRepository(private val addressApi: AddressApi) {

    suspend fun list(userId: Long) = addressApi.list(userId)

    suspend fun add(address: Map<String, Any>) = addressApi.add(address)

    suspend fun update(address: Map<String, Any>) = addressApi.update(address)

    suspend fun delete(id: Long, userId: Long) = addressApi.delete(id, userId)

    suspend fun setDefault(id: Long, userId: Long) = addressApi.setDefault(id, userId)
}