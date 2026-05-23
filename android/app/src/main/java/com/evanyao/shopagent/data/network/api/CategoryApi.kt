package com.evanyao.shopagent.data.network.api

import com.evanyao.shopagent.data.model.Category
import com.evanyao.shopagent.data.model.Result
import retrofit2.http.GET

interface CategoryApi {

    @GET("api/category/list")
    suspend fun getCategories(): Result<List<Category>>
}
