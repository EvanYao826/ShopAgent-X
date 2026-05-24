package com.evanyao.shopagent.data.network.api

import com.evanyao.shopagent.data.model.Result
import retrofit2.http.*

interface AddressApi {

    @GET("api/address/list")
    suspend fun list(@Query("userId") userId: Long): Result<List<@JvmSuppressWildcards Map<String, Any>>>

    @POST("api/address/add")
    suspend fun add(@Body address: @JvmSuppressWildcards Map<String, Any>): Result<@JvmSuppressWildcards Map<String, Any>>

    @PUT("api/address/update")
    suspend fun update(@Body address: @JvmSuppressWildcards Map<String, Any>): Result<@JvmSuppressWildcards Map<String, Any>>

    @DELETE("api/address/delete")
    suspend fun delete(@Query("id") id: Long, @Query("userId") userId: Long): Result<Void>

    @PUT("api/address/setDefault")
    suspend fun setDefault(@Query("id") id: Long, @Query("userId") userId: Long): Result<Void>
}