package com.evanyao.shopagent.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object ProfileSetup : Screen("profile_setup")
    object Chat : Screen("chat")
    object Profile : Screen("profile")
    object ProductList : Screen("product_list")
    object ProductDetail : Screen("product_detail/{productId}") {
        fun createRoute(productId: Long) = "product_detail/$productId"
    }
    object Cart : Screen("cart")
    object Favorites : Screen("favorites")
    object History : Screen("history")
    object Settings : Screen("settings")
}
