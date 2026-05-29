package com.evanyao.shopagent.di

import com.evanyao.shopagent.data.network.AuthInterceptor
import com.evanyao.shopagent.data.network.RetrofitClient
import com.evanyao.shopagent.data.network.SseClient
import com.evanyao.shopagent.data.network.api.AddressApi
import com.evanyao.shopagent.data.network.api.AuthApi
import com.evanyao.shopagent.data.network.api.CartApi
import com.evanyao.shopagent.data.network.api.CategoryApi
import com.evanyao.shopagent.data.network.api.ChatApi
import com.evanyao.shopagent.data.network.api.ProductApi
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit

val networkModule = module {
    // 用于刷新 Token 的 AuthApi（不带 AuthInterceptor，避免循环）
    single(qualifier = named("plainAuthApi")) { RetrofitClient.createPlainRetrofit().create(AuthApi::class.java) }
    single { AuthInterceptor(get()) { get(qualifier = named("plainAuthApi")) } }
    single { SseClient(get()) }
    single { RetrofitClient.createOkHttpClient(get()) }
    single { RetrofitClient.createRetrofit(get()) }
    // 带 AuthInterceptor 的 AuthApi（用于普通请求）
    single { get<Retrofit>().create(AuthApi::class.java) }
    single { get<Retrofit>().create(ChatApi::class.java) }
    single { get<Retrofit>().create(ProductApi::class.java) }
    single { get<Retrofit>().create(CategoryApi::class.java) }
    single { get<Retrofit>().create(CartApi::class.java) }
    single { get<Retrofit>().create(AddressApi::class.java) }
}