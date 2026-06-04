package com.evanyao.shopagent.navigation

/** 页面路由定义 */
sealed class Screen(val route: String) {
    object Login : Screen("login")                           // 登录
    object Register : Screen("register")                     // 注册
    object ProfileSetup : Screen("profile_setup")            // 用户画像引导
    object Chat : Screen("chat")                             // 对话
    object Profile : Screen("profile")                       // 我的
    object ProductList : Screen("product_list")              // 商品列表
    object ProductDetail : Screen("product_detail/{productId}") {
        fun createRoute(productId: Long) = "product_detail/$productId"
    }
    object Cart : Screen("cart")                             // 购物车
    object Favorites : Screen("favorites")                   // 收藏
    object History : Screen("history")                       // 浏览历史
    object Settings : Screen("settings")                     // 设置
    object EditProfile : Screen("edit_profile")              // 编辑资料
    object AddressList : Screen("address_list")              // 地址列表
    object AddressEdit : Screen("address_edit/{addressId}") {
        fun createRoute(addressId: Long? = null) = if (addressId != null) "address_edit/$addressId" else "address_edit/-1"
    }
    object About : Screen("about")                           // 关于
    object ChangePassword : Screen("change_password")        // 修改密码
    object OrderList : Screen("order_list")                  // 订单列表
    object OrderDetail : Screen("order_detail/{orderId}") {
        fun createRoute(orderId: Long) = "order_detail/$orderId"
    }
    object Checkout : Screen("checkout/{addressId}") {
        fun createRoute(addressId: Long? = null) = "checkout/${addressId ?: -1}"
    }
}
