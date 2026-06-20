package com.evanyao.shopagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.evanyao.shopagent.navigation.MainNavigation
import com.evanyao.shopagent.ui.theme.AppTheme
import org.koin.androidx.compose.KoinAndroidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏和导航栏图标设为深色，适配浅色背景
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            KoinAndroidContext {
                AppTheme {
                    MainNavigation()
                }
            }
        }
    }
}
