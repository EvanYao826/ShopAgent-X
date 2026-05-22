package com.evanyao.shopagent.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Chat : BottomNavItem(
        route = Screen.Chat.route,
        title = "对话",
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.Chat
    )

    object Product : BottomNavItem(
        route = Screen.ProductList.route,
        title = "商品",
        selectedIcon = Icons.Filled.ShoppingBag,
        unselectedIcon = Icons.Outlined.ShoppingBag
    )

    object Profile : BottomNavItem(
        route = Screen.Profile.route,
        title = "我的",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}
