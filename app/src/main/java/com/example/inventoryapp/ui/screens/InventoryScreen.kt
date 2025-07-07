package com.example.inventoryapp.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.InventoryFilters
import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.InventoryViewModel
import com.example.inventoryapp.model.UserRole
import com.example.inventoryapp.ui.components.InventoryCard
import kotlinx.coroutines.launch
import androidx.compose.runtime.livedata.observeAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    navController: NavController,
    viewModel: InventoryViewModel,
    inventoryRepo: InventoryRepository
) {
    val context = LocalContext.current
    var filterText by remember { mutableStateOf("") }
    val inventory by viewModel.inventory.observeAsState(emptyList())
    val loading by viewModel.loading.observeAsState(false)
    val error by viewModel.error.observeAsState(null)
    val filters by viewModel.filters.observeAsState(InventoryFilters())
    val role = viewModel.userRole

    var sortBy by remember { mutableStateOf("Date") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val sortedInventory = remember(sortBy, inventory) {
        when (sortBy) {
            "Date" -> inventory.sortedByDescending { it.date ?: 0L }
            "Name" -> inventory.sortedBy { it.name ?: "" }
            "Serial" -> inventory.sortedBy { it.serial }
            else -> inventory
        }
    }

    val filteredInventory = sortedInventory.filter { item ->
        (filters.serial.isNullOrBlank() || item.serial.contains(filters.serial!!, ignoreCase = true)) &&
        (filters.model.isNullOrBlank() || item.model?.contains(filters.model!!, ignoreCase = true) == true) &&
        (filterText.isBlank() || (item.name?.contains(filterText, true) == true || item.serial.contains(filterText, true) || item.model?.contains(filterText, true) == true))
    }

    var selectedSerials by remember { mutableStateOf(setOf<String>()) }
    val allSelected = filteredInventory.isNotEmpty() && filteredInventory.all { selectedSerials.contains(it.serial) }

    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var filterDialogVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // For image picker (for item images)
    var pickedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pickedImages = uris
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "Picked ${uris.size} image(s)", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .padding(paddingValues)
        ) {
            // Search bar with filter & barcode icons inside
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text("Search inventory...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { filterDialogVisible = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        IconButton(onClick = { navController.navigate("barcode_scanner") }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Sorting dropdown
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sort by: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Spacer(modifier = Modifier.height(8.dp))
            if (selectedSerials.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            selectedSerials = if (checked) filteredInventory.map { it.serial }.toSet() else emptySet()
                        }
                    )
                    Text("Selected (${selectedSerials.size})")
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = { showBatchDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Selected")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    items(filteredInventory) { item ->
                        InventoryCard(
                            item = item,
                            userRole = role,
                            onClick = { selectedItem = item },
                            onEdit = { editingItem = item },
                            onDelete = {
                                scope.launch {
                                    val result = inventoryRepo.deleteItem(item.serial)
                                    if (result is Result.Success) {
                                        viewModel.loadInventory()
                                        snackbarHostState.showSnackbar("Item deleted")
                                    } else if (result is Result.Error) {
                                        snackbarHostState.showSnackbar(result.exception?.message ?: "Delete failed!")
                                    }
                                }
                            },
                            onAddTransaction = { /* show transaction dialog or navigate if needed */ },
                            onViewHistory = { /* show history dialog or screen if needed */ },
                            onArchive = { /* archive not used */ },
                            onSelectionChange = { checked ->
                                selectedSerials = if (checked) selectedSerials + item.serial else selectedSerials - item.serial
                            },
                            isSelected = selectedSerials.contains(item.serial)
                        )
                    }
                }
            }

            // Item detail dialog
            if (selectedItem != null) {
                AlertDialog(
                    onDismissRequest = { selectedItem = null },
                    title = { Text(selectedItem?.name ?: "Item Details") },
                    text = { Text("Model: ${selectedItem?.model}\nSerial: ${selectedItem?.serial}\nQuantity: ${selectedItem?.quantity}") },
                    confirmButton = {
                        Button(onClick = { selectedItem = null }) { Text("Close") }
                    }
                )
            }

            // Filter dialog
            if (filterDialogVisible) {
                AlertDialog(
                    onDismissRequest = { filterDialogVisible = false },
                    title = { Text("Filter Options") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = filters.serial ?: "",
                                onValueChange = { viewModel.updateSerialFilter(it) },
                                label = { Text("Serial") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = filters.model ?: "",
                                onValueChange = { viewModel.updateModelFilter(it) },
                                label = { Text("Model") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { filterDialogVisible = false }) { Text("OK") }
                    }
                )
            }
        }
    }
}