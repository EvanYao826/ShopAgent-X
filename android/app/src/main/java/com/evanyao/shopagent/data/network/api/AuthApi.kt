package com.evanyao.shopagent.data.network.api

import com.evanyao.shopagent.data.model.LoginRequest
import com.evanyao.shopagent.data.model.ProfileUpdateRequest
import com.evanyao.shopagent.data.model.RegisterRequest
import com.evanyao.shopagent.data.model.Result
import com.evanyao.shopagent.data.model.User
import retrofit2.http.*

interface AuthApi {

    @POST("api/auth/sendCode")
    suspend fun sendCode(@Query("phone") phone: String): Result<String>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Result<Map<String, Any>>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Result<Map<String, Any>>

    @POST("api/auth/update")
    suspend fun updateUserInfo(@Body request: ProfileUpdateRequest): Result<User>

    @GET("api/auth/profile")
    suspend fun getProfile(): Result<User>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Header("Authorization") token: String): Result<Map<String, Any>>
}
