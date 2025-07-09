package com.example.inventoryapp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.inventoryapp.data.AuthRepository
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.model.UserRole
import com.example.inventoryapp.model.InventoryViewModel
import com.example.inventoryapp.ui.screens.*

sealed class MainScreen(val route: String, val label: String, val icon: ImageVector) {
    object Inventory : MainScreen("inventory", "Inventory", Icons.AutoMirrored.Filled.List)
    object Transaction : MainScreen("transaction", "Transaction", Icons.Filled.SwapHoriz)
    object Analytics : MainScreen("analytics", "Analytics", Icons.AutoMirrored.Filled.ShowChart)
    object TransactionHistory : MainScreen("transaction_history", "History", Icons.AutoMirrored.Filled.ShowChart)
}

@Composable
fun AppNavHost(
    authRepo: AuthRepository,
    inventoryRepo: InventoryRepository,
    navController: NavHostController,
    userRole: UserRole,
    modifier: Modifier = Modifier
) {
    var showBottomBar by remember { mutableStateOf(true) }

    val mainScreens = buildList {
        add(MainScreen.Inventory)
        add(MainScreen.Transaction)
        if (userRole == UserRole.ADMIN) add(MainScreen.Analytics)
        add(MainScreen.TransactionHistory)
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("?")
                    mainScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().route!!) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    // Add logout button
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Logout, contentDescription = "Logout") },
                        label = { Text("Logout") },
                        selected = false,
                        onClick = {
                            authRepo.logout()
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") {
                showBottomBar = false
                SplashScreen(navController, authRepo)
            }
            composable("login") {
                showBottomBar = false
                LoginScreen(navController, authRepo)
            }
            composable("register") {
                showBottomBar = false
                RegisterScreen(navController, authRepo)
            }
            composable(MainScreen.Inventory.route) {
                showBottomBar = true
                val inventoryViewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.provideFactory(inventoryRepo, userRole))
                InventoryScreen(navController, inventoryViewModel, inventoryRepo)
            }
            composable(MainScreen.Transaction.route) {
                showBottomBar = true
                TransactionScreen(navController, inventoryRepo, userRole)
            }
            if (userRole == UserRole.ADMIN) {
                composable(MainScreen.Analytics.route) {
                    showBottomBar = true
                    AnalyticsScreen(inventoryRepo)
                }
            }
            // Transaction screen with query params
            composable(
                route = "transaction_screen?type={type}&serial={serial}&model={model}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType; defaultValue = ""; nullable = true },
                    navArgument("serial") { type = NavType.StringType; defaultValue = ""; nullable = true },
                    navArgument("model") { type = NavType.StringType; defaultValue = ""; nullable = true }
                )
            ) { backStackEntry ->
                showBottomBar = false
                val type = backStackEntry.arguments?.getString("type") ?: ""
                val serial = backStackEntry.arguments?.getString("serial") ?: ""
                val model = backStackEntry.arguments?.getString("model") ?: ""
                TransactionScreen(
                    navController = navController,
                    inventoryRepo = inventoryRepo,
                    userRole = userRole,
                    prefillType = type,
                    prefillSerial = serial,
                    prefillModel = model
                )
            }
            composable(MainScreen.TransactionHistory.route) {
                showBottomBar = true
                TransactionHistoryScreen(
                    inventoryRepo = inventoryRepo,
                    navController = navController,
                    navToBarcodeScanner = { navController.navigate("barcode_scanner") }
                )
            }
            composable("barcode_scanner") {
                showBottomBar = false
                BarcodeScannerScreen(navController)
            }
        }
    }
}