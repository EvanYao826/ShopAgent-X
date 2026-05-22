package com.evanyao.shopagent.di

import com.evanyao.shopagent.data.TokenManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { TokenManager(androidContext()) }
}
