package com.example.inventoryapp.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.inventoryapp.model.InventoryFilters
import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.UserRole
import com.example.inventoryapp.ui.components.InventoryCard
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    navController: NavController,
    viewModel: InventoryViewModel,
    role: UserRole,
    navToBarcodeScanner: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var filterText by remember { mutableStateOf("") }
    var filterDialogVisible by remember { mutableStateOf(false) }

    val inventory by viewModel.inventory.collectAsState()
    var selectedSerials by remember { mutableStateOf(setOf<String>()) }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showPhotoViewer by remember { mutableStateOf(false) }
    var photoViewerImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var photoViewerStartIndex by remember { mutableStateOf(0) }
    var zoom by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }

    // Internet Connectivity
    val isConnected = remember {
        derivedStateOf {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.activeNetwork?.let { network ->
                cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: false
        }
    }

    // Snackbar on no internet
    LaunchedEffect(isConnected.value) {
        if (!isConnected.value) {
            snackbarHostState.showSnackbar("Internet is not connected. This page might be old")
        }
    }

    // Barcode scanner integration for search bar
    val scannedSerialLive = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<String>("scannedSerial")
    val scannedSerialState = scannedSerialLive?.observeAsState()
    val scannedSerial = scannedSerialState?.value
    LaunchedEffect(scannedSerial) {
        scannedSerial?.let { serial ->
            filterText = serial
            viewModel.searchInventory(serial)
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("scannedSerial")
        }
    }

    // Remove items with quantity zero, sold, or in repair
    LaunchedEffect(inventory) {
        inventory.forEach { item ->
            if (item.quantity <= 0 || item.isSold || item.isInRepair) {
                viewModel.deleteItem(item.serial)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                actions = {
                    IconButton(onClick = { viewModel.loadInventory() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { filterDialogVisible = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = {
                        if (navToBarcodeScanner != null) {
                            navToBarcodeScanner()
                        } else {
                            navController.navigate("barcode_scanner")
                        }
                    }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan IMEI/Serial")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp)
        ) {
            OutlinedTextField(
                value = filterText,
                onValueChange = {
                    filterText = it
                    viewModel.searchInventory(it)
                },
                placeholder = { Text("Search inventory...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            when {
                viewModel.loading.value -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                viewModel.error.value != null -> {
                    Text(viewModel.error.value ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                }
                inventory.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No inventory items found.")
                    }
                }
                else -> {
                    LazyColumn {
                        itemsIndexed(inventory, key = { _, item -> item.serial }) { _, item ->
                            InventoryCard(
                                item = item,
                                userRole = role,
                                onClick = { selectedItem = item },
                                onEdit = { navController.navigate("edit_inventory/${item.serial}") },
                                onDelete = {
                                    // Remove the item completely
                                    viewModel.deleteItem(item.serial)
                                    snackbarHostState.showSnackbar("Item deleted")
                                },
                                onAddTransaction = {
                                    navController.navigate("transaction?prefillSerial=${item.serial}&prefillModel=${item.model}")
                                },
                                onViewHistory = {
                                    navController.navigate("transaction_history?serial=${item.serial}")
                                },
                                onArchive = { /* archive not used */ },
                                onSelectionChange = { checked ->
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
            }
        }

        // Item detail dialog
        if (selectedItem != null) {
            AlertDialog(
                onDismissRequest = { selectedItem = null },
                title = { Text(selectedItem?.name ?: "Item Details") },
                text = {
                    Text(
                        "Model: ${selectedItem?.model}\nSerial: ${selectedItem?.serial}\nQuantity: ${selectedItem?.quantity}\nDescription: ${selectedItem?.description}"
                    )
                },
                confirmButton = {
                    Button(onClick = { selectedItem = null }) { Text("Close") }
                }
            )
        }

        // Photo viewer dialog (with pinch to zoom and download)
        if (showPhotoViewer && photoViewerImages.isNotEmpty()) {
            val imgUrl = photoViewerImages.getOrNull(photoViewerStartIndex)
            AlertDialog(
                onDismissRequest = { showPhotoViewer = false },
                confirmButton = {
                    Row {
                        Button(
                            onClick = {
                                val nextIdx = (photoViewerStartIndex + 1) % photoViewerImages.size
                                photoViewerStartIndex = nextIdx
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        ) {
                            Text("Next")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val prevIdx = (photoViewerStartIndex - 1 + photoViewerImages.size) % photoViewerImages.size
                                photoViewerStartIndex = prevIdx
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        ) {
                            Text("Previous")
                        }
                    }
                },
                dismissButton = {
                    Row {
                        Button(
                            onClick = {
                                downloading = true
                                imgUrl?.let { url ->
                                    // Download logic
                                    val fileName = "inventory_image_${System.currentTimeMillis()}.jpg"
                                    downloadImage(context, url, fileName,
                                        onDownloadComplete = {
                                            downloading = false
                                            snackbarHostState.showSnackbar("Downloaded to Pictures/$fileName")
                                        },
                                        onDownloadError = {
                                            downloading = false
                                            snackbarHostState.showSnackbar("Download failed")
                                        }
                                    )
                                }
                            },
                            enabled = !downloading
                        ) {
                            if (downloading) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            else Text("Download")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showPhotoViewer = false }) { Text("Close") }
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
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
                        imgUrl?.let {
                            AsyncImage(
                                model = it,
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
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            )
        }

        // Filter dialog (example, adjust as needed)
        if (filterDialogVisible) {
            AlertDialog(
                onDismissRequest = { filterDialogVisible = false },
                title = { Text("Filter Inventory", style = MaterialTheme.typography.headlineSmall) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = viewModel.filters.value.serial ?: "",
                            onValueChange = { viewModel.setFilters(viewModel.filters.value.copy(serial = it)) },
                            label = { Text("Serial Number") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = viewModel.filters.value.model ?: "",
                            onValueChange = { viewModel.setFilters(viewModel.filters.value.copy(model = it)) },
                            label = { Text("Model") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { filterDialogVisible = false }) { Text("Apply") }
                    }
                }
            )
        }
    }
}

// Utility function to download image from URL
fun downloadImage(
    context: Context,
    url: String,
    fileName: String,
    onDownloadComplete: () -> Unit,
    onDownloadError: () -> Unit
) {
    try {
        val input = java.net.URL(url).openStream()
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val file = File(picturesDir, fileName)
        val output = FileOutputStream(file)
        input.use { inp -> output.use { outp -> inp.copyTo(outp) } }
        onDownloadComplete()
    } catch (e: Exception) {
        onDownloadError()
    }
}