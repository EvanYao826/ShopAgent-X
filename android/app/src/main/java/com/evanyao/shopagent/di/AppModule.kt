package com.evanyao.shopagent.di

import com.evanyao.shopagent.data.TokenManager
import com.evanyao.shopagent.data.repository.AddressRepository
import com.evanyao.shopagent.data.repository.CartRepository
import com.evanyao.shopagent.data.repository.ChatRepository
import com.evanyao.shopagent.data.repository.ProductRepository
import com.evanyao.shopagent.viewmodel.AddressViewModel
import com.evanyao.shopagent.viewmodel.AuthViewModel
import com.evanyao.shopagent.viewmodel.CartViewModel
import com.evanyao.shopagent.viewmodel.ChatViewModel
import com.evanyao.shopagent.viewmodel.FavoriteViewModel
import com.evanyao.shopagent.viewmodel.HistoryViewModel
import com.evanyao.shopagent.viewmodel.ProductViewModel
import com.evanyao.shopagent.viewmodel.ProfileViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { TokenManager(androidContext()) }
    single { ChatRepository(get()) }
    single { ProductRepository(get(), get()) }
    single { CartRepository(get()) }
    single { AddressRepository(get()) }
    viewModel { AuthViewModel(get(), get()) }
    viewModel { ChatViewModel(get(), get()) }
    viewModel { ProductViewModel(get()) }
    viewModel { CartViewModel(get()) }
    viewModel { ProfileViewModel(get(), get()) }
    viewModel { FavoriteViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { AddressViewModel(get()) }
}