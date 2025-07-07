package com.example.inventoryapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.inventoryapp.data.AuthRepository
import com.example.inventoryapp.data.FirebaseInventoryRepository
import com.example.inventoryapp.model.UserRole
import com.example.inventoryapp.ui.navigation.AppNavHost
import com.example.inventoryapp.ui.theme.InventoryAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InventoryAppTheme {
                val navController = rememberNavController()
                val authRepo = AuthRepository()
                val inventoryRepo = FirebaseInventoryRepository()
                val userRole: UserRole = authRepo.getCurrentUserRole()
                AppNavHost(
                    authRepo = authRepo,
                    inventoryRepo = inventoryRepo,
                    navController = navController,
                    userRole = userRole,
                    modifier = Modifier
                )
            }
        }
    }
}