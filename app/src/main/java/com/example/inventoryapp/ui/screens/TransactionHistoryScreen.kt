package com.example.inventoryapp.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCodeScanner
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
import coil.request.ImageRequest
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.Transaction
import com.example.inventoryapp.model.UserRole
import com.example.inventoryapp.utils.downloadImage
import com.example.inventoryapp.ui.components.TransactionHistoryCard
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    inventoryRepo: InventoryRepository,
    navController: NavController? = null,
    userRole: UserRole,
    navToBarcodeScanner: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var selectedTx by remember { mutableStateOf<Transaction?>(null) }
    var searchText by remember { mutableStateOf("") }
    var filterDialogVisible by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Date") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var selectedSaleType by remember { mutableStateOf<String?>(null) }
    var fromDate by remember { mutableStateOf<Date?>(null) }
    var toDate by remember { mutableStateOf<Date?>(null) }
    var valueRange by remember { mutableStateOf<Pair<Double?, Double?>?>(null) }

    var fromDatePickerOpen by remember { mutableStateOf(false) }
    var toDatePickerOpen by remember { mutableStateOf(false) }
    var fromDateString by remember { mutableStateOf("") }
    var toDateString by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var showImageViewer by remember { mutableStateOf(false) }
    var viewerImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewerStartIndex by remember { mutableStateOf(0) }
    var zoom by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = inventoryRepo.getAllTransactions()
        if (result is Result.Success) {
            transactions = result.data.sortedByDescending { it.timestamp }
        } else {
            error = (result as? Result.Error)?.exception?.message
        }
    }

    if (navController != null) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        LaunchedEffect(savedStateHandle?.get<String>("scannedSerial")) {
            val scannedSerial = savedStateHandle?.get<String>("scannedSerial")
            if (!scannedSerial.isNullOrBlank()) {
                searchText = scannedSerial
                savedStateHandle.remove<String>("scannedSerial")
            }
        }
    }

    val filteredTx = transactions
        .filter { tx ->
            (searchText.isBlank() ||
                tx.serial.contains(searchText, true) ||
                tx.model.contains(searchText, true) ||
                tx.type.contains(searchText, true)) &&
            (selectedSaleType == null || tx.type.equals(selectedSaleType, true)) &&
            (fromDate == null || tx.timestamp >= fromDate!!.time) &&
            (toDate == null || tx.timestamp <= toDate!!.time) &&
            (valueRange == null || (
                tx.amount >= (valueRange?.first ?: 0.0) &&
                tx.amount <= (valueRange?.second ?: Double.MAX_VALUE)
            ))
        }
        .let {
            when (sortBy) {
                "Date" -> it.sortedByDescending { tx -> tx.timestamp }
                "Type" -> it.sortedBy { tx -> tx.type }
                "Amount" -> it.sortedByDescending { tx -> tx.amount }
                else -> it
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction History") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search history...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { filterDialogVisible = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        IconButton(onClick = {
                            if (navToBarcodeScanner != null) {
                                navToBarcodeScanner()
                            } else if (navController != null) {
                                navController.navigate("barcode_scanner")
                            }
                        }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(sortBy)
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Date") },
                        onClick = {
                            sortBy = "Date"
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Type") },
                        onClick = {
                            sortBy = "Type"
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Amount") },
                        onClick = {
                            sortBy = "Amount"
                            sortMenuExpanded = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredTx.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions available.")
                }
            } else {
                LazyColumn {
                    items(filteredTx) { tx ->
                        TransactionHistoryCard(
                            transaction = tx,
                            onClick = {
                                selectedTx = tx
                                viewerImages = tx.images
                                viewerStartIndex = 0
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                                showImageViewer = false
                            },
                            backgroundColor = when (tx.type.lowercase()) {
                                "sale" -> Color(0xFF4CAF50)
                                "purchase" -> Color(0xFF2196F3)
                                "repair" -> Color(0xFFFFA726)
                                "return" -> Color(0xFFBDBDBD)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    }
                }
            }
        }

        if (filterDialogVisible) {
            AlertDialog(
                onDismissRequest = { filterDialogVisible = false },
                title = {
                    Text("Filter Transactions", style = MaterialTheme.typography.headlineSmall)
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Transaction Type", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            val saleTypes = listOf("sale", "purchase", "return", "repair")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(saleTypes) { type ->
                                    FilterChip(
                                        selected = selectedSaleType == type,
                                        onClick = { selectedSaleType = if (selectedSaleType == type) null else type },
                                        label = { Text(type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Date Range", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = fromDateString,
                                    onValueChange = { },
                                    label = { Text("From", style = MaterialTheme.typography.labelMedium) },
                                    placeholder = { Text("yyyy-MM-dd") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { fromDatePickerOpen = true },
                                    shape = RoundedCornerShape(12.dp),
                                    readOnly = true
                                )
                                OutlinedTextField(
                                    value = toDateString,
                                    onValueChange = { },
                                    label = { Text("To", style = MaterialTheme.typography.labelMedium) },
                                    placeholder = { Text("yyyy-MM-dd") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { toDatePickerOpen = true },
                                    shape = RoundedCornerShape(12.dp),
                                    readOnly = true
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                selectedSaleType = null
                                fromDate = null
                                toDate = null
                                fromDateString = ""
                                toDateString = ""
                                valueRange = null
                                filterDialogVisible = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Clear All")
                        }
                        Button(
                            onClick = { filterDialogVisible = false },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Apply Filters")
                        }
                    }
                }
            )
        }

        if (fromDatePickerOpen) {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, dayOfMonth)
                    fromDateString = dateFormat.format(picked.time)
                    fromDate = picked.time
                    fromDatePickerOpen = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }
        if (toDatePickerOpen) {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, dayOfMonth)
                    toDateString = dateFormat.format(picked.time)
                    toDate = picked.time
                    toDatePickerOpen = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }

        selectedTx?.let { tx ->
            AlertDialog(
                onDismissRequest = { selectedTx = null },
                title = { Text("Transaction Details") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("Type: ${tx.type}")
                        Text("Model: ${tx.model}")
                        Text("Serial: ${tx.serial}")
                        Text("Customer: ${tx.customerName}")
                        Text("Phone: ${tx.phoneNumber}")
                        Text("Aadhaar: ${tx.aadhaarNumber}")
                        Text("Amount: ${tx.amount}")
                        Text("Date: ${tx.date}")
                        Text("Description: ${tx.description}")
                        if (tx.deletedInfo != null) {
                            Text(
                                "deleted by ${tx.deletedInfo.deletedBy} at ${tx.deletedInfo.deletedAt}",
                                color = Color.Red,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (tx.images.isNotEmpty()) {
                            Text("Photos:")
                            LazyRow {
                                items(tx.images) { imgUrl ->
                                    Box(
                                        Modifier
                                            .size(140.dp)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black.copy(alpha = 0.9f))
                                            .clickable {
                                                viewerImages = tx.images
                                                viewerStartIndex = tx.images.indexOf(imgUrl)
                                                showImageViewer = true
                                                zoom = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(imgUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { selectedTx = null }) { Text("Close") }
                }
            )
        }

        if (showImageViewer && viewerImages.isNotEmpty()) {
            val imgUrl = viewerImages.getOrNull(viewerStartIndex)
            AlertDialog(
                onDismissRequest = { showImageViewer = false },
                confirmButton = {
                    Row {
                        Button(
                            onClick = {
                                val nextIdx = (viewerStartIndex + 1) % viewerImages.size
                                viewerStartIndex = nextIdx
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        ) { Text("Next") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val prevIdx = (viewerStartIndex - 1 + viewerImages.size) % viewerImages.size
                                viewerStartIndex = prevIdx
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        ) { Text("Previous") }
                    }
                },
                dismissButton = {
                    Row {
                        Button(
                            onClick = {
                                downloading = true
                                imgUrl?.let { url ->
                                    val fileName = "transaction_image_${System.currentTimeMillis()}.jpg"
                                    downloadImage(context, url, fileName,
                                        onDownloadComplete = {
                                            downloading = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Image downloaded to Pictures directory")
                                            }
                                        },
                                        onDownloadError = {
                                            downloading = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Failed to download image")
                                            }
                                        }
                                    )
                                }
                            },
                            enabled = !downloading
                        ) {
                            if (downloading)
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            else
                                Icon(Icons.Default.Download, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showImageViewer = false }) { Text("Close") }
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
                                contentDescription = "Transaction Image",
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth()
                                    .graphicsLayer(
                                        scaleX = zoom,
                                        scaleY = zoom,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    ),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                    }
                }
            )
        }
    }
}

/*
Where will the image be stored?
--------------------------------
The image will be downloaded to the app's external files directory for pictures.

More specifically:
- The directory: context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
- The file name: "transaction_image_<timestamp>.jpg"
- Absolute path will usually be: /storage/emulated/0/Android/data/<your.package.name>/files/Pictures/transaction_image_<timestamp>.jpg

If you want the image to appear in the device's gallery, MediaStore API should be used. The current implementation stores it in the app's private pictures directory.
*/