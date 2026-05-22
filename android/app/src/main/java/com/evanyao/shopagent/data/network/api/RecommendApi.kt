package com.evanyao.shopagent.data.network.api

import com.evanyao.shopagent.data.model.Result
import retrofit2.http.*

interface RecommendApi {

    @POST("api/recommend/browse")
    suspend fun recordBrowse(@Body request: Map<String, Any>): Result<Void>

    @POST("api/recommend/favorite/add")
    suspend fun addFavorite(
        @Query("userId") userId: Long,
        @Query("productId") productId: Long
    ): Result<Void>

    @POST("api/recommend/favorite/remove")
    suspend fun removeFavorite(
        @Query("userId") userId: Long,
        @Query("productId") productId: Long
    ): Result<Void>

    @GET("api/recommend/favorite/list")
    suspend fun getFavorites(@Query("userId") userId: Long): Result<List<Any>>

    @GET("api/recommend/browse/history")
    suspend fun getBrowseHistory(@Query("userId") userId: Long): Result<List<Any>>
}
