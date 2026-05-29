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
    object EditProfile : Screen("edit_profile")
    object AddressList : Screen("address_list")
    object AddressEdit : Screen("address_edit/{addressId}") {
        fun createRoute(addressId: Long? = null) = if (addressId != null) "address_edit/$addressId" else "address_edit/-1"
    }
    object About : Screen("about")
    object ChangePassword : Screen("change_password")
    object OrderList : Screen("order_list")
    object OrderDetail : Screen("order_detail/{orderId}") {
        fun createRoute(orderId: Long) = "order_detail/$orderId"
    }
    object Checkout : Screen("checkout/{addressId}") {
        fun createRoute(addressId: Long? = null) = "checkout/${addressId ?: -1}"
    }
}
