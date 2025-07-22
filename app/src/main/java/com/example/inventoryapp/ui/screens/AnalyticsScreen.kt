package com.example.inventoryapp.ui.screens

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.Transaction
import com.example.inventoryapp.model.UserRole
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@SuppressLint("RememberReturnType")
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
    val types =
        remember(transactions) { listOf("All") + transactions.map { it.type }.distinct().sorted() }
    val models = remember(transactions) {
        listOf("All") + transactions.mapNotNull { it.model }.distinct().sorted()
    }

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
    val startDateLong =
        startDate.takeIf { it.isNotBlank() }?.let { sdf.parse(it)?.time } ?: Long.MIN_VALUE
    val endDateLong =
        endDate.takeIf { it.isNotBlank() }?.let { sdf.parse(it)?.time } ?: Long.MAX_VALUE

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
                onMaxAmountChange = { maxAmount = it }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Total Sales: ₹$totalSales",
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
            Text(
                "Total Purchases: ₹$totalPurchases",
                color = Color(0xFF2196F3),
                fontWeight = FontWeight.Bold
            )
            Text(
                "Total Repairs: ₹$totalRepairs",
                color = Color(0xFFFFA726),
                fontWeight = FontWeight.Bold
            )
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
) {
    var startDate by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }
    var endDate by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var startDatePickerDialog by remember { mutableStateOf(false) }
    var endDatePickerDialog by remember { mutableStateOf(false) }

    if (startDatePickerDialog) {
        val calendar = Calendar.getInstance()
        val parts = startDate.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(y, m, d)
                if (!selectedCal.after(Calendar.getInstance())) {
                    startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedCal.time)
                } else {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Future dates are not allowed.") }
                }
                startDatePickerDialog = false
            },
            year, month, day
        ).apply {
            datePicker.maxDate = calendar.timeInMillis
        }.show()
    }

    if (endDatePickerDialog) {
        val calendar = Calendar.getInstance()
        val parts = endDate.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(y, m, d)
                if (!selectedCal.after(Calendar.getInstance())) {
                    endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedCal.time)
                } else {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Future dates are not allowed.") }
                }
                endDatePickerDialog = false
            },
            year, month, day
        ).apply {
            datePicker.maxDate = calendar.timeInMillis
        }.show()
    }

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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Start Date",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            OutlinedButton(
                onClick = { startDatePickerDialog = true },
                modifier = Modifier.padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFEAF1FB)
                )
            ) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (startDate.isBlank()) "Pick Start Date" else startDate,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "End Date",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            OutlinedButton(
                onClick = { endDatePickerDialog = true },
                modifier = Modifier.padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFEAF1FB)
                )
            ) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (endDate.isBlank()) "Pick End Date" else endDate,
                    color = MaterialTheme.colorScheme.primary
                )
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
            Text(
                "Type: ${tx.type} | Model: ${tx.model} | Amount: ₹${tx.amount}",
                fontWeight = FontWeight.Bold
            )
            Text("Serial: ${tx.serial}")
            Text("Date: ${tx.date}")
            Text("Customer: ${tx.customerName}")
        }
    }
}
