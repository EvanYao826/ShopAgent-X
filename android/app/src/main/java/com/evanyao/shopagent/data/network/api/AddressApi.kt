package com.evanyao.shopagent.data.network.api

import com.evanyao.shopagent.data.model.AddressRequest
import com.evanyao.shopagent.data.model.AddressResponse
import com.evanyao.shopagent.data.model.Result
import retrofit2.http.*

interface AddressApi {

    @GET("api/address/list")
    suspend fun list(): Result<List<AddressResponse>>

    @POST("api/address/add")
    suspend fun add(@Body address: AddressRequest): Result<AddressResponse>

    @PUT("api/address/update")
    suspend fun update(@Body address: AddressRequest): Result<AddressResponse>

    @DELETE("api/address/delete")
    suspend fun delete(@Query("id") id: Long): Result<Void>

    @PUT("api/address/setDefault")
    suspend fun setDefault(@Query("id") id: Long): Result<Void>
}
