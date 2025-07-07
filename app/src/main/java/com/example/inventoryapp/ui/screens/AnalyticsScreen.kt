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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.Transaction
import com.google.firebase.analytics.FirebaseAnalytics
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(inventoryRepo: InventoryRepository) {
    val transactions = remember { mutableStateListOf<Transaction>() }
    val firebaseAnalytics = FirebaseAnalytics.getInstance(LocalContext.current)

    // Load transactions once
    LaunchedEffect(Unit) {
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
    val filtered = transactions.filter { tx ->
        (selectedType == "All" || tx.type.equals(selectedType, ignoreCase = true)) &&
        (selectedModel == "All" || (tx.model?.equals(selectedModel, ignoreCase = true) == true)) &&
        (minAmount.toDoubleOrNull()?.let { tx.amount >= it } ?: true) &&
        (maxAmount.toDoubleOrNull()?.let { tx.amount <= it } ?: true) &&
        (startDate.isBlank() || tx.date >= startDate) &&
        (endDate.isBlank() || tx.date <= endDate)
    }

    // Totals by type for visuals
    val totalsByType = filtered.groupBy { it.type }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
    val typeColors = listOf(
        Color(0xFF4CAF50), // Sale
        Color(0xFF2196F3), // Purchase
        Color(0xFFFFC107), // Return
        Color(0xFFF44336)  // Repair
    )
    val typeColorMap = types.filter { it != "All" }.mapIndexed { idx, type -> type to typeColors.getOrElse(idx) { Color.Gray } }.toMap()

    val totalAmount = filtered.sumOf { it.amount }
    val totalSales = filtered.filter { it.type.equals("Sale", true) }.sumOf { it.amount }
    val totalPurchases = filtered.filter { it.type.equals("Purchase", true) }.sumOf { it.amount }
    val totalReturns = filtered.filter { it.type.equals("Return", true) }.sumOf { it.amount }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownMenuBox(
                    label = "Type",
                    options = types,
                    selectedOption = selectedType,
                    onOptionSelected = { selectedType = it }
                )
                Spacer(Modifier.width(8.dp))
                DropdownMenuBox(
                    label = "Model",
                    options = models,
                    selectedOption = selectedModel,
                    onOptionSelected = { selectedModel = it }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    value = minAmount,
                    onValueChange = { minAmount = it },
                    label = { Text("Min Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = maxAmount,
                    onValueChange = { maxAmount = it },
                    label = { Text("Max Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Start Date") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("End Date") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Totals
            Text("Total: $totalAmount")
            Text("Sales: $totalSales")
            Text("Purchases: $totalPurchases")
            Text("Returns: $totalReturns")
            Text("Repairs: $totalRepairs")

            Spacer(Modifier.height(16.dp))

            // List
            if (filtered.isEmpty()) {
                Text("No transactions for selected filters.")
            } else {
                LazyColumn {
                    filtered.forEach { tx ->
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = typeColorMap[tx.type] ?: Color.LightGray
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Type: ${tx.type} | Model: ${tx.model}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "Amount: ${tx.amount} | Date: ${tx.date}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
    Box(Modifier.padding(4.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label: $selectedOption")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}