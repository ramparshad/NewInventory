package com.example.inventoryapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.Transaction
import com.example.inventoryapp.ui.components.TransactionHistoryCard
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    inventoryRepo: InventoryRepository,
    navToBarcodeScanner: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var selectedTx by remember { mutableStateOf<Transaction?>(null) }
    var searchText by remember { mutableStateOf("") }
    var filterDialogVisible by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Date") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    // Load transactions
    LaunchedEffect(Unit) {
        val result = inventoryRepo.getAllTransactions()
        if (result is Result.Success) {
            transactions = result.data.sortedByDescending { it.timestamp }
        } else {
            error = (result as? Result.Error)?.exception?.message
        }
    }

    // Apply search, filter, sort
    val filteredTx = transactions
        .filter { tx ->
            searchText.isBlank() ||
                (tx.serial?.contains(searchText, true) == true) ||
                (tx.model?.contains(searchText, true) == true) ||
                (tx.type?.contains(searchText, true) == true)
        }
        .let {
            when (sortBy) {
                "Date" -> it.sortedByDescending { tx -> tx.timestamp }
                "Type" -> it.sortedBy { tx -> tx.type ?: "" }
                "Amount" -> it.sortedByDescending { tx -> tx.amount ?: 0.0 }
                else -> it
            }
        }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Transaction History") }) }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize()) {
            // Search bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search history...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { filterDialogVisible = true }) {
                            Icon(Icons.Default.FilterList, "Filter")
                        }
                        IconButton(onClick = { navToBarcodeScanner?.invoke() }) {
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
                                DropdownMenuItem(text = { Text("Date") }, onClick = {
                                    sortBy = "Date"; sortMenuExpanded = false
                                })
                                DropdownMenuItem(text = { Text("Type") }, onClick = {
                                    sortBy = "Type"; sortMenuExpanded = false
                                })
                                DropdownMenuItem(text = { Text("Amount") }, onClick = {
                                    sortBy = "Amount"; sortMenuExpanded = false
                                })
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )

            if (filteredTx.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions available.")
                }
            } else {
                LazyColumn {
                    items(filteredTx) { tx ->
                        TransactionHistoryCard(
                            transaction = tx,
                            onClick = { selectedTx = tx }
                        )
                    }
                }
            }

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
        }
        // Transaction detail dialog
        selectedTx?.let { tx ->
            AlertDialog(
                onDismissRequest = { selectedTx = null },
                title = { Text("Transaction Details") },
                text = { Text("Type: ${tx.type}\nModel: ${tx.model}\nSerial: ${tx.serial}\nAmount: ${tx.amount}\nDate: ${tx.date}\nDescription: ${tx.description ?: ""}") },
                confirmButton = {
                    Button(onClick = { selectedTx = null }) { Text("Close") }
                }
            )
        }
    }
}