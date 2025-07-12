package com.example.inventoryapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.Transaction
import com.example.inventoryapp.model.UserRole
import com.google.firebase.analytics.FirebaseAnalytics
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    inventoryRepo: InventoryRepository,
    userRole: UserRole
) {
    // Only allow admin users
    if (userRole != UserRole.ADMIN) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Analytics available to admin accounts only.", color = Color.Red)
        }
        return
    }

    val transactions = remember { mutableStateListOf<Transaction>() }
    val firebaseAnalytics = FirebaseAnalytics.getInstance(LocalContext.current)

    // Log when the analytics screen is viewed
    LaunchedEffect(Unit) {
        firebaseAnalytics.logEvent("analytics_screen_viewed", null)
        val result = inventoryRepo.getAllTransactions()
        if (result is Result.Success) {
            transactions.clear()
            transactions.addAll(result.data)
        }
    }

    // Generate filter options
    val types = remember(transactions) { listOf("All") + transactions.map { it.type }.distinct().sorted() }
    val models = remember(transactions) { listOf("All") + transactions.mapNotNull { it.model }.distinct().sorted() }

    // Filter state
    var selectedType by remember { mutableStateOf("All") }
    var selectedModel by remember { mutableStateOf("All") }
    var minAmount by remember { mutableStateOf("") }
    var maxAmount by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    // Analytics: log filter changes
    LaunchedEffect(selectedType, selectedModel, minAmount, maxAmount, startDate, endDate) {
        val bundle = Bundle().apply {
            putString("type", selectedType)
            putString("model", selectedModel)
            putString("min_amount", minAmount)
            putString("max_amount", maxAmount)
            putString("start_date", startDate)
            putString("end_date", endDate)
        }
        firebaseAnalytics.logEvent("analytics_filter_changed", bundle)
    }

    // Filtering logic
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val startDateLong = startDate.takeIf { it.isNotBlank() }?.let { sdf.parse(it)?.time } ?: Long.MIN_VALUE
    val endDateLong = endDate.takeIf { it.isNotBlank() }?.let { sdf.parse(it)?.time } ?: Long.MAX_VALUE

    val filtered = transactions.filter { tx ->
        (selectedType == "All" || tx.type.equals(selectedType, ignoreCase = true)) &&
        (selectedModel == "All" || tx.model.equals(selectedModel, ignoreCase = true)) &&
        (minAmount.toDoubleOrNull()?.let { tx.amount >= it } ?: true) &&
        (maxAmount.toDoubleOrNull()?.let { tx.amount <= it } ?: true) &&
        (tx.timestamp in startDateLong..endDateLong)
    }

    val totalSales = filtered.filter { it.type.equals("Sale", true) }.sumOf { it.amount }
    val totalPurchases = filtered.filter { it.type.equals("Purchase", true) }.sumOf { it.amount }
    val totalRepairs = filtered.filter { it.type.equals("Repair", true) }.sumOf { it.amount }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Analytics / Stats") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Filters UI
            AnalyticsFilters(
                types = types,
                models = models,
                selectedType = selectedType,
                onTypeSelected = { selectedType = it },
                selectedModel = selectedModel,
                onModelSelected = { selectedModel = it },
                minAmount = minAmount,
                onMinAmountChange = { minAmount = it },
                maxAmount = maxAmount,
                onMaxAmountChange = { maxAmount = it },
                startDate = startDate,
                onStartDateChange = { startDate = it },
                endDate = endDate,
                onEndDateChange = { endDate = it }
            )

            Spacer(Modifier.height(16.dp))
            Text("Total Sales: ₹$totalSales", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            Text("Total Purchases: ₹$totalPurchases", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
            Text("Total Repairs: ₹$totalRepairs", color = Color(0xFFFFA726), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Divider()

            LazyColumn {
                items(filtered) { tx ->
                    TransactionStatsCard(tx)
                }
            }
        }
    }
}

@Composable
fun AnalyticsFilters(
    types: List<String>,
    models: List<String>,
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    minAmount: String,
    onMinAmountChange: (String) -> Unit,
    maxAmount: String,
    onMaxAmountChange: (String) -> Unit,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    endDate: String,
    onEndDateChange: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DropdownMenuBox(
            label = "Type",
            options = types,
            selectedOption = selectedType,
            onOptionSelected = onTypeSelected
        )
        Spacer(Modifier.width(8.dp))
        DropdownMenuBox(
            label = "Model",
            options = models,
            selectedOption = selectedModel,
            onOptionSelected = onModelSelected
        )
    }
    Spacer(Modifier.height(8.dp))
    Row {
        OutlinedTextField(
            value = minAmount,
            onValueChange = onMinAmountChange,
            label = { Text("Min Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = maxAmount,
            onValueChange = onMaxAmountChange,
            label = { Text("Max Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(8.dp))
    Row {
        OutlinedTextField(
            value = startDate,
            onValueChange = onStartDateChange,
            label = { Text("Start Date (yyyy-MM-dd)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = endDate,
            onValueChange = onEndDateChange,
            label = { Text("End Date (yyyy-MM-dd)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DropdownMenuBox(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            label = { Text(label) },
            modifier = Modifier
                .width(140.dp)
                .clickable { expanded = true },
            readOnly = true
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onOptionSelected(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun TransactionStatsCard(tx: Transaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Type: ${tx.type} | Model: ${tx.model} | Amount: ₹${tx.amount}", fontWeight = FontWeight.Bold)
            Text("Serial: ${tx.serial}")
            Text("Date: ${tx.date}")
            Text("Customer: ${tx.customerName}")
        }
    }
}