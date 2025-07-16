package com.example.inventoryapp.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.model.UserRole
import com.example.inventoryapp.ui.components.TransactionForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    navController: NavController,
    inventoryRepo: InventoryRepository,
    userRole: UserRole,
    prefillType: String? = null,
    prefillSerial: String? = null,
    prefillModel: String? = null,
    requiredFields: List<String> = listOf("serial", "model", "amount", "date"),
    navToBarcodeScanner: (() -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val showSuccess = remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Transaction") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        TransactionForm(
            navController = navController,
            inventoryRepo = inventoryRepo,
            userRole = userRole,
            requiredFields = requiredFields,
            snackbarHostState = snackbarHostState,
            showSuccess = showSuccess,
            prefillType = prefillType,
            prefillSerial = prefillSerial,
            prefillModel = prefillModel,
            modifier = Modifier.padding(paddingValues)
        )
    }
}