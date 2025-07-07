package com.example.inventoryapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    requiredFields: List<String> = listOf("serial", "model", "amount", "date")
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val showSuccess = remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var filterDialogVisible by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Date") }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                // Search bar with filter, barcode, and sort
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("Search transactions...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { filterDialogVisible = true }) {
                                Icon(Icons.Default.FilterList, "Filter")
                            }
                            IconButton(onClick = { navController.navigate("barcode_scanner") }) {
                                Icon(Icons.Default.QrCodeScanner, "Barcode")
                            }
                            Box {
                                TextButton(onClick = { sortMenuExpanded = true }) {
                                    Text(sortBy)
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(text = { Text("Date") }, onClick = { sortBy = "Date"; sortMenuExpanded = false })
                                    DropdownMenuItem(text = { Text("Type") }, onClick = { sortBy = "Type"; sortMenuExpanded = false })
                                    DropdownMenuItem(text = { Text("Amount") }, onClick = { sortBy = "Amount"; sortMenuExpanded = false })
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            if (filterDialogVisible) {
                AlertDialog(
                    onDismissRequest = { filterDialogVisible = false },
                    title = { Text("Filter Options") },
                    text = { Text("Add filter controls here") },
                    confirmButton = {
                        Button(onClick = { filterDialogVisible = false }) { Text("OK") }
                    }
                )
            }
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
                modifier = Modifier
            )
        }
    }
}