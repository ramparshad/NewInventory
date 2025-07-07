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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.Transaction
import com.example.inventoryapp.ui.components.TransactionHistoryCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    inventoryRepo: InventoryRepository,
    navController: NavController? = null, // Pass navController if using navigation!
    navToBarcodeScanner: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var selectedTx by remember { mutableStateOf<Transaction?>(null) }
    var searchText by remember { mutableStateOf("") }
    var filterDialogVisible by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Date") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    // Filter state
    var selectedSaleType by remember { mutableStateOf<String?>(null) }
    var fromDate by remember { mutableStateOf<Date?>(null) }
    var toDate by remember { mutableStateOf<Date?>(null) }
    var valueRange by remember { mutableStateOf<Pair<Double?, Double?>?>(null) }

    // Date pickers state
    var fromDateString by remember { mutableStateOf("") }
    var toDateString by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Load transactions
    LaunchedEffect(Unit) {
        val result = inventoryRepo.getAllTransactions()
        if (result is Result.Success) {
            transactions = result.data.sortedByDescending { it.timestamp }
        } else {
            error = (result as? Result.Error)?.exception?.message
        }
    }

    // --- Barcode scan result handling ---
    // Use a SavedStateHandle mechanism to receive scanned serial if navController is provided
    if (navController != null) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        // Listen for scannedSerial result and update searchText
        LaunchedEffect(savedStateHandle?.get<String>("scannedSerial")) {
            val scannedSerial = savedStateHandle?.get<String>("scannedSerial")
            if (!scannedSerial.isNullOrBlank()) {
                searchText = scannedSerial
                savedStateHandle.remove<String>("scannedSerial")
            }
        }
    }

    // Filtering logic
    val filteredTx = transactions
        .filter { tx ->
            // Search text
            (searchText.isBlank() ||
                    (tx.serial?.contains(searchText, true) == true) ||
                    (tx.model?.contains(searchText, true) == true) ||
                    (tx.type?.contains(searchText, true) == true))
            // Sale type
            && (selectedSaleType == null || tx.type.equals(selectedSaleType, true))
            // Date
            && (fromDate == null || (tx.timestamp >= fromDate!!.time))
            && (toDate == null || (tx.timestamp <= toDate!!.time))
            // Value
            && (valueRange == null || (
                    (tx.amount ?: 0.0) >= (valueRange?.first ?: 0.0) &&
                    (tx.amount ?: 0.0) <= (valueRange?.second ?: Double.MAX_VALUE))
                )
        }
        .let {
            when (sortBy) {
                "Date" -> it.sortedByDescending { tx -> tx.timestamp }
                "Type" -> it.sortedBy { tx -> tx.type ?: "" }
                "Amount" -> it.sortedByDescending { tx -> tx.amount ?: 0.0 }
                else -> it
            }
        }

    // --- UI ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Search history...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { filterDialogVisible = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = {
                        // If navToBarcodeScanner is provided, use it, else fallback to navController.navigate
                        if (navToBarcodeScanner != null) {
                            navToBarcodeScanner()
                        } else if (navController != null) {
                            navController.navigate("barcode_scanner")
                        }
                    }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode")
                    }
                    Box {
                        TextButton(onClick = { sortMenuExpanded = true }) {
                            Text(sortBy)
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Date") },
                                onClick = {
                                    sortBy = "Date"
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Type") },
                                onClick = {
                                    sortBy = "Type"
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Amount") },
                                onClick = {
                                    sortBy = "Amount"
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

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

        // Filter dialog
        if (filterDialogVisible) {
            AlertDialog(
                onDismissRequest = { filterDialogVisible = false },
                title = { Text("Filter Options") },
                text = {
                    Column {
                        // Sale type
                        Text("Sale Type")
                        val saleTypes = listOf("sale", "purchase", "return", "repair")
                        Row {
                            saleTypes.forEach { type ->
                                FilterChip(
                                    selected = selectedSaleType == type,
                                    onClick = { selectedSaleType = if (selectedSaleType == type) null else type },
                                    label = { Text(type.replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        // Date range
                        Text("Date Range")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = fromDateString,
                                onValueChange = {
                                    fromDateString = it
                                    fromDate = try { dateFormat.parse(it) } catch (_: Exception) { null }
                                },
                                label = { Text("From (yyyy-MM-dd)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = toDateString,
                                onValueChange = {
                                    toDateString = it
                                    toDate = try { dateFormat.parse(it) } catch (_: Exception) { null }
                                },
                                label = { Text("To (yyyy-MM-dd)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        // Value range
                        Text("Value Range")
                        val ranges = listOf(
                            Pair(null, 5000.0) to "<5000",
                            Pair(5000.0, 10000.0) to "5000-10000",
                            Pair(10000.0, 20000.0) to "10000-20000",
                            Pair(20000.0, 30000.0) to "20000-30000",
                            Pair(30000.0, 45000.0) to "30000-45000",
                            Pair(45000.0, null) to "above 45000"
                        )
                        Row {
                            ranges.forEach { (range, label) ->
                                FilterChip(
                                    selected = valueRange == range,
                                    onClick = {
                                        valueRange = if (valueRange == range) null else range
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { filterDialogVisible = false }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        selectedSaleType = null
                        fromDate = null
                        toDate = null
                        fromDateString = ""
                        toDateString = ""
                        valueRange = null
                        filterDialogVisible = false
                    }) {
                        Text("Clear")
                    }
                }
            )
        }
    }

    // Transaction detail dialog
    selectedTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { selectedTx = null },
            title = { Text("Transaction Details") },
            text = {
                Text(
                    "Type: ${tx.type}\n" +
                    "Model: ${tx.model}\n" +
                    "Serial: ${tx.serial}\n" +
                    "Amount: ${tx.amount}\n" +
                    "Date: ${tx.date}\n" +
                    "Description: ${tx.description ?: ""}"
                )
            },
            confirmButton = {
                Button(onClick = { selectedTx = null }) { Text("Close") }
            }
        )
    }
}