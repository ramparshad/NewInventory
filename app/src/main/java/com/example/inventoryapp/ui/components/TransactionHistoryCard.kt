package com.example.inventoryapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.example.inventoryapp.model.Transaction

@Composable
fun TransactionHistoryCard(
    transaction: Transaction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Model: ${transaction.model} | Serial: ${transaction.serial}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Type: ${transaction.type} | Amount: ${transaction.amount}", style = MaterialTheme.typography.bodySmall)
            Text("Date: ${transaction.date}", style = MaterialTheme.typography.bodySmall)
        }
    }
}