package com.example.inventoryapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.InventoryViewModel
import com.example.inventoryapp.model.UserRole
import androidx.compose.runtime.livedata.observeAsState 

@Composable
fun InventoryDetailDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    viewModel: InventoryViewModel,
    userRole: UserRole,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val history by viewModel.transactionHistory.observeAsState(emptyList()) 

    LaunchedEffect(item.serial) { viewModel.loadTransactionHistory(item.serial) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.model ?: "Unnamed Item") },
        text = {
            Column {
                Text("Serial: ${item.serial}")
                Text("Name: ${item.name}")
                Text("Description: ${item.description}")
                Spacer(Modifier.height(8.dp))
                Text("Transaction History:")
                history.forEach { tx ->
                    Text("- ${tx.type} on ${tx.date}")
                }
            }
        },
        confirmButton = {
            Row {
                if (userRole == UserRole.ADMIN) {
                    IconButton(onClick = { onEdit?.invoke() }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete Item?") },
                            text = { Text("Are you sure you want to delete this item?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        onDelete?.invoke()
                                        showDeleteConfirm = false
                                    }
                                ) { Text("Delete") }
                            },
                            dismissButton = {
                                Button(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
                Button(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}