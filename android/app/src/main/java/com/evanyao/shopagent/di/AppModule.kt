package com.evanyao.shopagent.di

import com.evanyao.shopagent.data.TokenManager
import com.evanyao.shopagent.data.repository.ChatRepository
import com.evanyao.shopagent.viewmodel.AuthViewModel
import com.evanyao.shopagent.viewmodel.ChatViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { TokenManager(androidContext()) }
    single { ChatRepository(get()) }
    viewModel { AuthViewModel(get(), get()) }
    viewModel { ChatViewModel(get(), get()) }
}
