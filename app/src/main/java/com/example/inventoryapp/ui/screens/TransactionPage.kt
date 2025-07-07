package com.example.inventoryapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.model.InventoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionPage(
    transactionType: String,
    serial: String?,
    inventoryRepo: InventoryRepository,
    onBarcodeScan: (() -> Unit)? = null
) {
    var item by remember { mutableStateOf<InventoryItem?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var customerName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var filterDialogVisible by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Date") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val transactionTypes = listOf("Sale", "Repair", "Purchase", "Return")
    var selectedTransactionType by remember { mutableStateOf(transactionType.ifBlank { transactionTypes[0] }) }

    // Data loading
    LaunchedEffect(serial) {
        if (serial != null) {
            loading = true
            try {
                item = inventoryRepo.getItemBySerial(serial)
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            Column {
                // Search bar with filter & barcode & sort
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("Search item...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { filterDialogVisible = true }) {
                                Icon(Icons.Default.FilterList, "Filter")
                            }
                            IconButton(onClick = { onBarcodeScan?.invoke() }) {
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
                                    DropdownMenuItem(text = { Text("Name") }, onClick = { sortBy = "Name"; sortMenuExpanded = false })
                                    DropdownMenuItem(text = { Text("Serial") }, onClick = { sortBy = "Serial"; sortMenuExpanded = false })
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                // Transaction type dropdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Transaction Type: ", modifier = Modifier.padding(end = 8.dp))
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(selectedTransactionType)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            transactionTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedTransactionType = type
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (selectedTransactionType.lowercase()) {
                    "sale" -> "Sell Item"
                    "repair" -> "Repair Item"
                    "purchase" -> "Purchase Item"
                    "return" -> "Return Item"
                    else -> "Transaction"
                },
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            if (loading) CircularProgressIndicator()
            error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
            item?.let {
                Text("Item: ${it.name ?: ""}")
                Text("Model: ${it.model ?: ""}")
                Text("Serial: ${it.serial ?: ""}")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("Customer Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    // TODO: Save transaction logic
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Submit Transaction")
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
    }
}