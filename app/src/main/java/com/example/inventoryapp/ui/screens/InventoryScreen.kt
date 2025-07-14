package com.example.inventoryapp.ui.screens

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.inventoryapp.model.InventoryFilters
import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.InventoryViewModel
import com.example.inventoryapp.model.UserRole
import com.example.inventoryapp.ui.components.InventoryCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.Dialog
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    navController: NavController,
    viewModel: InventoryViewModel,
    inventoryRepo: com.example.inventoryapp.data.InventoryRepository
) {
    val context = LocalContext.current
    var filterText by remember { mutableStateOf("") }
    val inventory by viewModel.inventory.observeAsState(emptyList())
    val loading by viewModel.loading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val filters by viewModel.filters.observeAsState(InventoryFilters())
    val role = viewModel.userRole
    val sortBy by viewModel.sortBy.collectAsState()

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedSerials by remember { mutableStateOf(setOf<String>()) }
    val allSelected = inventory.isNotEmpty() && inventory.all { selectedSerials.contains(it.serial) }

    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var filterDialogVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showPhotoViewer by remember { mutableStateOf(false) }
    var photoViewerImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var photoViewerStartIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    var zoom by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }

    var lastInventory by remember { mutableStateOf<List<InventoryItem>>(emptyList()) }
    LaunchedEffect(inventory) { lastInventory = inventory }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val result = inventoryRepo.getAllItems(limit = 100)
            if (result is com.example.inventoryapp.data.Result.Success && result.data != lastInventory) {
                viewModel.loadInventory()
            }
        }
    }

    LaunchedEffect(inventory) {
        inventory.forEach { item ->
            if (item.quantity <= 0 || item.isSold || item.isInRepair) {
                scope.launch { inventoryRepo.deleteItem(item.serial) }
            }
        }
    }

    val scannedSerialLive = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<String>("scannedSerial")
    val scannedSerialState = scannedSerialLive?.observeAsState()
    val scannedSerial = scannedSerialState?.value
    LaunchedEffect(scannedSerial) {
        scannedSerial?.let { serial ->
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
            OutlinedTextField(
                value = filterText,
                onValueChange = {
                    filterText = it
                    viewModel.searchInventory(it)
                },
                placeholder = { Text("Search inventory...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { filterDialogVisible = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        IconButton(onClick = { navController.navigate("barcode_scanner") }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode")
                        }
                        IconButton(onClick = { viewModel.loadInventory() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))

            when {
                loading == true -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                }
                inventory.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No inventory items found.")
                }
                else -> LazyColumn {
                    itemsIndexed(inventory, key = { _, item -> item.serial }) { _, item ->
                        InventoryCard(
                            item = item,
                            userRole = role,
                            onClick = { selectedItem = item },
                            onEdit = { /* implement if needed */ },
                            onDelete = {
                                scope.launch {
                                    val result = inventoryRepo.deleteItem(item.serial)
                                    if (result is com.example.inventoryapp.data.Result.Success) {
                                        viewModel.loadInventory()
                                        snackbarHostState.showSnackbar("Item deleted")
                                    } else if (result is com.example.inventoryapp.data.Result.Error) {
                                        snackbarHostState.showSnackbar(result.exception?.message ?: "Delete failed!")
                                    }
                                }
                            },
                            onAddTransaction = { /* implement if needed */ },
                            onViewHistory = { /* implement if needed */ },
                            onArchive = { /* archive not used */ },
                            onSelectionChange = { checked: Boolean ->
                                selectedSerials = if (checked) selectedSerials + item.serial else selectedSerials - item.serial
                            },
                            isSelected = selectedSerials.contains(item.serial),
                            imageUrls = item.imageUrls,
                            onImageClick = { imgIdx: Int ->
                                photoViewerImages = item.imageUrls
                                photoViewerStartIndex = imgIdx
                                showPhotoViewer = true
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        )
                    }
                }
            }

            if (selectedItem != null) {
                AlertDialog(
                    onDismissRequest = { selectedItem = null },
                    title = { Text(selectedItem?.name ?: "Item Details") },
                    text = { Text("Model: ${selectedItem?.model}\nSerial: ${selectedItem?.serial}\nQuantity: ${selectedItem?.quantity}\nDescription: ${selectedItem?.description}") },
                    confirmButton = {
                        Button(onClick = { selectedItem = null }) { Text("Close") }
                    }
                )
            }

            if (showPhotoViewer && photoViewerImages.isNotEmpty()) {
                Dialog(onDismissRequest = { showPhotoViewer = false }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = photoViewerImages.getOrNull(photoViewerStartIndex),
                            contentDescription = "Inventory Image",
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                        )

                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) {
                            photoViewerImages.forEachIndexed { idx, _ ->
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .background(if (photoViewerStartIndex == idx) Color.White else Color.Gray, CircleShape)
                                        .clickable { photoViewerStartIndex = idx }
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        IconButton(
                            onClick = {
                                downloading = true
                                val url = photoViewerImages.getOrNull(photoViewerStartIndex)
                                url?.let { downloadImage(context, it, "inventory_image_${System.currentTimeMillis()}.jpg",
                                    onDownloadComplete = {
                                        downloading = false
                                    },
                                    onDownloadError = {
                                        downloading = false
                                    }
                                ) }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                            enabled = !downloading
                        ) {
                            if (downloading) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            else Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                        }
                        IconButton(
                            onClick = { showPhotoViewer = false },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.Photo, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }

            if (filterDialogVisible) {
                AlertDialog(
                    onDismissRequest = { filterDialogVisible = false },
                    title = { Text("Filter Inventory", style = MaterialTheme.typography.headlineSmall) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = filters.serial ?: "",
                                onValueChange = { viewModel.setFilters(filters.copy(serial = it)) },
                                label = { Text("Serial Number") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = filters.model ?: "",
                                onValueChange = { viewModel.setFilters(filters.copy(model = it)) },
                                label = { Text("Model") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = filters.quantity?.toString() ?: "",
                                onValueChange = { value: String ->
                                    val q = value.toIntOrNull()
                                    viewModel.setFilters(filters.copy(quantity = q))
                                },
                                label = { Text("Quantity") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = filters.date ?: "",
                                onValueChange = { viewModel.setFilters(filters.copy(date = it)) },
                                label = { Text("Date (yyyy-MM-dd)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    viewModel.setFilters(InventoryFilters())
                                    filterDialogVisible = false
                                }
                            ) { Text("Clear") }
                            Button(
                                onClick = { filterDialogVisible = false },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Apply") }
                        }
                    }
                )
            }
        }
    }
}

fun downloadImage(
    context: android.content.Context,
    url: String,
    fileName: String,
    onDownloadComplete: () -> Unit,
    onDownloadError: () -> Unit
) {
    try {
        val input = java.net.URL(url).openStream()
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val file = java.io.File(picturesDir, fileName)
        val output = java.io.FileOutputStream(file)
        input.use { inp -> output.use { outp -> inp.copyTo(outp) } }
        onDownloadComplete()
    } catch (e: Exception) {
        onDownloadError()
    }
}