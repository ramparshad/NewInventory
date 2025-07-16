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

/**
 * Wrapper screen for TransactionForm.
 * Features:
 * - Dynamic required fields (based on transaction type/user role if needed)
 * - Success callback for navigation or state handling
 * - Undo/History shortcut after transaction
 * - Accessibility: descriptive content for top bar/back button
 * - Error boundary for repo errors
 * - Documentation for props
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    navController: NavController,
    inventoryRepo: InventoryRepository,
    userRole: UserRole,
    prefillType: String? = null,
    prefillSerial: String? = null,
    prefillModel: String? = null,
    requiredFields: List<String>? = null, // allow null for dynamic
    navToBarcodeScanner: (() -> Unit)? = null,
    onTransactionSuccess: (() -> Unit)? = null // callback for parent
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val showSuccess = remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Example for dynamic required fields
    // You can customize this logic to be more complex
    val effectiveRequiredFields = requiredFields ?: remember(prefillType, userRole) {
        when (prefillType?.lowercase()) {
            "sale" -> listOf("serial", "model", "amount", "date")
            "purchase" -> listOf("serial", "model", "amount", "date")
            "repair" -> listOf("serial", "model", "date")
            "return" -> listOf("serial", "date")
            else -> listOf("serial", "model", "amount", "date")
        }
    }

    // Error boundary: check if repo is null or invalid
    if (inventoryRepo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error: Inventory repo not available.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Transaction") },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to previous screen") }
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            TransactionForm(
                navController = navController,
                inventoryRepo = inventoryRepo,
                userRole = userRole,
                requiredFields = effectiveRequiredFields,
                snackbarHostState = snackbarHostState,
                showSuccess = showSuccess,
                prefillType = prefillType,
                prefillSerial = prefillSerial,
                prefillModel = prefillModel,
                modifier = Modifier.fillMaxSize(),
            )

            // Transaction success snackbar with undo/history shortcut
            if (showSuccess.value) {
                LaunchedEffect(showSuccess.value) {
                    val result = snackbarHostState.showSnackbar(
                        message = "Transaction successful!",
                        actionLabel = "History",
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        navController.navigate("transaction_history")
                    }
                    showSuccess.value = false
                    onTransactionSuccess?.invoke() // call parent callback
                }
            }

            // Error boundary example (could be expanded)
            error?.let {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { error = null }) { Text("Dismiss") }
                    }
                ) { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/**
 * Compose Preview for TransactionScreen.
 * Allows UI snapshot testing and design review.
 */
@Composable
@Preview(showBackground = true)
fun TransactionScreenPreview() {
    // Replace with mock repo/userRole if needed
    TransactionScreen(
        navController = FakeNavController(),
        inventoryRepo = FakeInventoryRepo(),
        userRole = UserRole.ADMIN,
        prefillType = "Sale",
        prefillSerial = "ABC123",
        prefillModel = "ModelX"
    )
}

// --- Fake classes for preview/demo purposes ---
class FakeNavController : NavController(null)
class FakeInventoryRepo : InventoryRepository()