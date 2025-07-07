package com.example.inventoryapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTopBar(navController: NavController) {
    TopAppBar(
        title = { Text("Inventory Transaction") },
        actions = {
            IconButton(onClick = { navController.navigate("transaction_history") }) {
                Icon(Icons.Filled.History, contentDescription = "Transaction History")
            }
            IconButton(onClick = { navController.navigate("analytics") }) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Analytics/Stats")
            }
        }
    )
}