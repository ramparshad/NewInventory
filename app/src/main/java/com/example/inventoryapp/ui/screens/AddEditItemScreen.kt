package com.example.inventoryapp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inventoryapp.model.InventoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemDialog(
    originalItem: InventoryItem,
    onDismiss: () -> Unit,
    onSave: (InventoryItem) -> Unit
) {
    var name by remember { mutableStateOf(originalItem.name ?: "") }
    var model by remember { mutableStateOf(originalItem.model ?: "") }
    var serial by remember { mutableStateOf(originalItem.serial) }
    var description by remember { mutableStateOf(originalItem.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") })
                OutlinedTextField(value = serial, onValueChange = { serial = it }, label = { Text("Serial") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    originalItem.copy(
                        name = name,
                        model = model,
                        serial = serial,
                        description = description
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}