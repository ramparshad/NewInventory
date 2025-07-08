package com.example.inventoryapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.Transaction
import com.google.firebase.analytics.FirebaseAnalytics
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    inventoryRepo: InventoryRepository
) {
    val transactions = remember { mutableStateListOf<Transaction>() }
    val firebaseAnalytics = FirebaseAnalytics.getInstance(LocalContext.current)

    // Log when the reports screen is viewed
    LaunchedEffect(Unit) {
        firebaseAnalytics.logEvent("reports_viewed", null)
        val result = inventoryRepo.getAllTransactions()
        if (result is Result.Success) {
            transactions.clear()
            transactions.addAll(result.data)
        }
    }

    // Group transactions by formatted date string
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val transactionsByDate: Map<String, List<Transaction>> =
        transactions.groupBy { if (it.date.isNotBlank()) it.date else sdf.format(Date(it.timestamp)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reports") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (transactions.isEmpty()) {
                Text("No transactions available.")
            } else {
                transactionsByDate.forEach { (date, txList) ->
                    Text("Date: $date", style = MaterialTheme.typography.titleMedium)
                    txList.forEach { tx ->
                        Text("Serial: ${tx.serial} | Type: ${tx.type} | Model: ${tx.model} | Amount: ${tx.amount}")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}