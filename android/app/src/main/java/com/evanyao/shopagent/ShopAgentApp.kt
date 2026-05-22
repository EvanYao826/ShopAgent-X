package com.evanyao.shopagent

import android.app.Application
import com.evanyao.shopagent.di.appModule
import com.evanyao.shopagent.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ShopAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@ShopAgentApp)
            modules(appModule, networkModule)
        }
    }
}
