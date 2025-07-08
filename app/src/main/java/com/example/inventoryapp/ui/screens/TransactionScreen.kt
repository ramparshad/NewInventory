package com.example.inventoryapp.ui.screens

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.Transaction
import com.example.inventoryapp.model.UserRole
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    navToBarcodeScanner: (() -> Unit)? = null // optional lambda for barcode navigation
) {
    val context = LocalContext.current
    val firebaseAnalytics = remember { FirebaseAnalytics.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Form State
    var type by remember { mutableStateOf(prefillType ?: "") }
    var serial by remember { mutableStateOf(prefillSerial ?: "") }
    var model by remember { mutableStateOf(prefillModel ?: "") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var description by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    // UI State
    var searchText by remember { mutableStateOf("") }
    var filterDialogVisible by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Date") }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Validation Function
    fun isFormValid(): Boolean {
        return (!requiredFields.contains("serial") || serial.isNotBlank()) &&
                (!requiredFields.contains("model") || model.isNotBlank()) &&
                (!requiredFields.contains("amount") || amount.toDoubleOrNull() != null) &&
                (!requiredFields.contains("date") || date.isNotBlank()) &&
                (!requiredFields.contains("type") || type.isNotBlank())
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Search bar at the top
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { filterDialogVisible = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        IconButton(onClick = { navToBarcodeScanner?.invoke() ?: navController.navigate("barcode_scanner") }) {
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
                                    onClick = { sortBy = "Date"; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Type") },
                                    onClick = { sortBy = "Type"; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Amount") },
                                    onClick = { sortBy = "Amount"; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

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

            Spacer(Modifier.height(8.dp))

            // Transaction Form UI
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type") },
                    enabled = userRole == UserRole.ADMIN || userRole == UserRole.MANAGER,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serial,
                    onValueChange = { serial = it },
                    label = { Text("Serial") },
                    trailingIcon = {
                        IconButton(onClick = { navToBarcodeScanner?.invoke() ?: navController.navigate("barcode_scanner") }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Serial")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        scope.launch {
                            submitting = true
                            if (!isFormValid()) {
                                snackbarHostState.showSnackbar("Please fill all required fields correctly.")
                                submitting = false
                                return@launch
                            }
                            val doubleAmount = amount.toDoubleOrNull()
                            if (doubleAmount == null) {
                                snackbarHostState.showSnackbar("Please enter a valid amount.")
                                submitting = false
                                return@launch
                            }
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val parsedDate: Long = try {
                                sdf.parse(date)?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }
                            val transaction = Transaction(
                                type = type,
                                serial = serial,
                                model = model,
                                amount = doubleAmount,
                                date = date,
                                timestamp = parsedDate,
                                description = description.ifBlank { "" }
                            )
                            val result = inventoryRepo.addTransaction(serial, transaction)
                            submitting = false
                            if (result is Result.Success) {
                                snackbarHostState.showSnackbar("Transaction added successfully")
                                val bundle = Bundle().apply {
                                    putString("type", type)
                                    putString("serial", serial)
                                    putString("model", model)
                                    putDouble("amount", doubleAmount)
                                    putString("date", date)
                                }
                                firebaseAnalytics.logEvent("transaction_created", bundle)
                                navController.popBackStack()
                            } else {
                                snackbarHostState.showSnackbar(
                                    (result as? Result.Error)?.exception?.message
                                        ?: "Failed to add transaction"
                                )
                            }
                        }
                    },
                    enabled = !submitting && isFormValid(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (submitting) "Submitting..." else "Submit")
                }
            }
        }
    }
}