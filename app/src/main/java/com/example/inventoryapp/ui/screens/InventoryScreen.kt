package com.example.inventoryapp.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.InventoryFilters
import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.InventoryViewModel
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
    val sortBy = viewModel.sortBy.collectAsState().value

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedSerials by remember { mutableStateOf(setOf<String>()) }
    val allSelected = inventory.isNotEmpty() && inventory.all { selectedSerials.contains(it.serial) }

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

    // Always reload data if blank and not loading, plus auto-refresh every 30 seconds
    LaunchedEffect(inventory, loading) {
        if (inventory.isEmpty() && !loading) {
            viewModel.loadInventory()
        }
    }

    // Auto-refresh inventory every 30 seconds to show latest data
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000) // 30 seconds
            if (!loading) {
                viewModel.loadInventory()
            }
        }
    }

    // Collect scanned serial from barcode scanner
    val scannedSerial = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<String>("scannedSerial")?.observeAsState()
    LaunchedEffect(scannedSerial?.value) {
        scannedSerial?.value?.let { serial ->
            viewModel.updateSerialFilter(serial)
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("scannedSerial")
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
                onValueChange = {
                    filterText = it
                    viewModel.searchInventory(it)
                },
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
                        DropdownMenuItem(text = { Text("Date") }, onClick = { viewModel.setSortBy("Date"); sortMenuExpanded = false })
                        DropdownMenuItem(text = { Text("Name") }, onClick = { viewModel.setSortBy("Name"); sortMenuExpanded = false })
                        DropdownMenuItem(text = { Text("Serial") }, onClick = { viewModel.setSortBy("Serial"); sortMenuExpanded = false })
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedSerials.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            selectedSerials = if (checked) inventory.map { it.serial }.toSet() else emptySet()
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

            when {
                loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                }
                inventory.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No inventory items found.")
                }
                else -> LazyColumn {
                    items(inventory) { item ->
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