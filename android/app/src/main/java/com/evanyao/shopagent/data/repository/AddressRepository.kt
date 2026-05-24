package com.evanyao.shopagent.data.repository

import com.evanyao.shopagent.data.network.api.AddressApi

class AddressRepository(private val addressApi: AddressApi) {

    suspend fun list() = addressApi.list()

    suspend fun add(address: Map<String, Any>) = addressApi.add(address)

    suspend fun update(address: Map<String, Any>) = addressApi.update(address)

    suspend fun delete(id: Long) = addressApi.delete(id)

    suspend fun setDefault(id: Long) = addressApi.setDefault(id)
}
