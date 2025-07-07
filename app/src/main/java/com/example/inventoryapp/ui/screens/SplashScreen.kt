package com.example.inventoryapp.ui.screens

import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.example.inventoryapp.data.AuthRepository
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController, authRepo: AuthRepository) {
    LaunchedEffect(Unit) {
        delay(1200)
        if (authRepo.getCurrentUserRole() != null) {
            navController.navigate("inventory") {
                popUpTo("splash") { inclusive = true }
            }
        } else {
            navController.navigate("login") {
                popUpTo("splash") { inclusive = true }
            }
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}