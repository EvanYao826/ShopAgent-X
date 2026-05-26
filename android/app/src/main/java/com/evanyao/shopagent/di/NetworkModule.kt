package com.evanyao.shopagent.di

import com.evanyao.shopagent.data.network.AuthInterceptor
import com.evanyao.shopagent.data.network.RetrofitClient
import com.evanyao.shopagent.data.network.api.AuthApi
import com.evanyao.shopagent.data.network.api.CategoryApi
import com.evanyao.shopagent.data.network.api.ChatApi
import com.evanyao.shopagent.data.network.api.ProductApi
import com.evanyao.shopagent.data.network.api.RecommendApi
import org.koin.dsl.module
import retrofit2.Retrofit

val networkModule = module {
    single { AuthInterceptor(get()) }
    single { RetrofitClient.createOkHttpClient(get()) }
    single { RetrofitClient.createRetrofit(get()) }
    single { get<Retrofit>().create(AuthApi::class.java) }
    single { get<Retrofit>().create(ChatApi::class.java) }
    single { get<Retrofit>().create(ProductApi::class.java) }
    single { get<Retrofit>().create(CategoryApi::class.java) }
    single { get<Retrofit>().create(RecommendApi::class.java) }
}
