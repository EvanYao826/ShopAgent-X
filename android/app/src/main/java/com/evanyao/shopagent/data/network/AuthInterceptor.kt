package com.evanyao.shopagent.data.network

import android.util.Log
import com.evanyao.shopagent.data.TokenManager
import com.evanyao.shopagent.data.network.api.AuthApi
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val authApiProvider: () -> AuthApi
) : Interceptor {

    private val isRefreshing = AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = runBlocking { tokenManager.getToken() }

        Log.d("AuthInterceptor", "Request: ${original.url}, Token: ${token?.take(20)}...")

        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        val response = chain.proceed(request)

        if ((response.code != 401 && response.code != 403) || token == null) {
            return response
        }

        response.close()

        synchronized(this) {
            val currentToken = runBlocking { tokenManager.getToken() }
            if (currentToken != token) {
                return chain.proceed(
                    original.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                )
            }

            if (!isRefreshing.compareAndSet(false, true)) {
                Thread.sleep(100)
                val newToken = runBlocking { tokenManager.getToken() } ?: return response
                return chain.proceed(
                    original.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                )
            }

            try {
                val refreshToken = runBlocking { tokenManager.getRefreshToken() }
                    ?: return response

                val refreshResponse = runBlocking {
                    try {
                        authApiProvider().refreshToken("Bearer $refreshToken")
                    } catch (e: Exception) {
                        Log.e("AuthInterceptor", "Refresh token request failed", e)
                        null
                    }
                }

                if (refreshResponse?.isSuccess == true && refreshResponse.data != null) {
                    val newToken = refreshResponse.data["token"] as? String
                    if (newToken != null) {
                        runBlocking { tokenManager.saveToken(newToken) }
                        return chain.proceed(
                            original.newBuilder()
                                .header("Authorization", "Bearer $newToken")
                                .build()
                        )
                    }
                }

                Log.e("AuthInterceptor", "Refresh token failed: ${refreshResponse?.message}")
                return response
            } finally {
                isRefreshing.set(false)
            }
        }
    }
}
