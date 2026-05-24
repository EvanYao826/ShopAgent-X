package com.evanyao.shopagent.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.evanyao.shopagent.ui.screens.auth.LoginScreen
import com.evanyao.shopagent.ui.screens.auth.ProfileSetupScreen
import com.evanyao.shopagent.ui.screens.auth.RegisterScreen
import com.evanyao.shopagent.ui.screens.cart.CartScreen
import com.evanyao.shopagent.ui.screens.chat.ChatScreen
import com.evanyao.shopagent.ui.screens.product.ProductDetailScreen
import com.evanyao.shopagent.ui.screens.product.ProductListScreen
import com.evanyao.shopagent.ui.screens.profile.ProfileScreen
import com.evanyao.shopagent.ui.screens.profile.SettingsScreen
import com.evanyao.shopagent.viewmodel.AuthViewModel
import com.evanyao.shopagent.viewmodel.CartViewModel
import com.evanyao.shopagent.viewmodel.ChatViewModel
import com.evanyao.shopagent.viewmodel.ProductViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = koinViewModel()
    val chatViewModel: ChatViewModel = koinViewModel()
    val productViewModel: ProductViewModel = koinViewModel()
    val cartViewModel: CartViewModel = koinViewModel()
    val authState by authViewModel.uiState.collectAsState()
    val cartState by cartViewModel.uiState.collectAsState()

    val bottomNavItems = listOf(
        BottomNavItem.Chat,
        BottomNavItem.Product,
        BottomNavItem.Cart,
        BottomNavItem.Profile
    )

    // 监听登录状态变化，自动跳转
    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) {
            chatViewModel.clearState()
            chatViewModel.loadConversations()
            if (authState.isProfileSetupDone) {
                chatViewModel.loadRecommendations()
                cartViewModel.refreshOnLogin()
                navController.navigate(Screen.Cart.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            } else {
                navController.navigate(Screen.ProfileSetup.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        val cartItemCount = cartState.cartItems.size
                        NavigationBarItem(
                            icon = {
                                if (item is BottomNavItem.Cart && cartItemCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(text = if (cartItemCount > 99) "99+" else "$cartItemCount")
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.title
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                }
                            },
                            label = { Text(item.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLogin = { phone, password ->
                        authViewModel.login(phone, password)
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    },
                    isLoading = authState.isLoading,
                    errorMessage = authState.errorMessage
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegister = { phone, code, password, username ->
                        authViewModel.register(phone, code, password, username)
                    },
                    onSendCode = { phone ->
                        authViewModel.sendCode(phone)
                    },
                    onNavigateToLogin = {
                        navController.popBackStack()
                    },
                    isLoading = authState.isLoading,
                    errorMessage = authState.errorMessage
                )
            }
            composable(Screen.ProfileSetup.route) {
                // 监听引导页完成，异步保存成功后再加载推荐
                LaunchedEffect(authState.isProfileSetupDone) {
                    if (authState.isProfileSetupDone) {
                        chatViewModel.loadRecommendations()
                        cartViewModel.refreshOnLogin()
                        navController.navigate(Screen.Cart.route) {
                            popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                        }
                    }
                }
                ProfileSetupScreen(
                    onComplete = { gender, ageRange, skinType, tags ->
                        authViewModel.saveProfileSetup(gender, ageRange, skinType, tags)
                    },
                    onSkip = {
                        authViewModel.skipProfileSetup()
                    }
                )
            }
            composable(Screen.Chat.route) {
                ChatScreen(
                    viewModel = chatViewModel,
                    onProductClick = { productId ->
                        navController.navigate(Screen.ProductDetail.createRoute(productId))
                    }
                )
            }
            composable(Screen.ProductList.route) {
                ProductListScreen(
                    viewModel = productViewModel,
                    onProductClick = { productId ->
                        navController.navigate(Screen.ProductDetail.createRoute(productId))
                    }
                )
            }
            composable(
                route = Screen.ProductDetail.route,
                arguments = listOf(navArgument("productId") { type = NavType.LongType })
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getLong("productId") ?: return@composable
                ProductDetailScreen(
                    viewModel = productViewModel,
                    productId = productId,
                    onBack = { navController.popBackStack() },
                    onAddToCart = { id, skuId -> cartViewModel.addToCart(id, skuId) }
                )
            }
            composable(Screen.Cart.route) {
                CartScreen(
                    viewModel = cartViewModel,
                    onProductClick = { productId ->
                        navController.navigate(Screen.ProductDetail.createRoute(productId))
                    },
                    onCheckout = {}
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    username = authState.username,
                    phone = authState.phone,
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onFavoritesClick = { },
                    onHistoryClick = { }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        authViewModel.logout()
                        chatViewModel.clearState()
                        cartViewModel.clearError()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
