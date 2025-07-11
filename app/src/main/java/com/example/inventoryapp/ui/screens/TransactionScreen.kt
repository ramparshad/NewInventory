package com.example.inventoryapp.ui.screens

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.Transaction
import com.example.inventoryapp.model.UserRole
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    navController: NavController,
    inventoryRepo: InventoryRepository,
    userRole: UserRole,
    prefillType: String? = null,
    prefillSerial: String? = null,
    prefillModel: String? = null,
    requiredFields: List<String> = listOf("serial", "model", "amount", "date"),
    navToBarcodeScanner: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // States
    var selectedTransactionType by remember { mutableStateOf(prefillType ?: "Sale") }
    var loading by remember { mutableStateOf(false) }

    // Form fields
    var serialNumber by remember { mutableStateOf(prefillSerial ?: "") }
    var modelName by remember { mutableStateOf(prefillModel ?: "") }
    var customerName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var aadhaarNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var transactionDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var datePickerDialogOpen by remember { mutableStateOf(false) }

    // Field errors
    var serialError by remember { mutableStateOf<String?>(null) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var customerNameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var aadhaarError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var quantityError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if ((selectedImages.size + uris.size) <= 5) {
            selectedImages = selectedImages + uris
        }
    }

    // Camera launcher
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            if (selectedImages.size < 5) {
                selectedImages = selectedImages + cameraImageUri!!
            }
        }
    }

    fun createImageUri(): Uri {
        val imagesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = kotlin.runCatching {
            java.io.File.createTempFile(
                "pic_${System.currentTimeMillis()}",
                ".jpg",
                imagesDir
            )
        }.getOrNull() ?: java.io.File(imagesDir, "pic_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }

    // Validation functions
    fun validateForm(): Boolean {
        var isValid = true

        if (serialNumber.isBlank()) {
            serialError = "Serial number is required"
            isValid = false
        } else {
            serialError = null
        }

        if (modelName.isBlank()) {
            modelError = "Model name is required"
            isValid = false
        } else {
            modelError = null
        }

        if (customerName.isBlank()) {
            customerNameError = "Customer name is required"
            isValid = false
        } else {
            customerNameError = null
        }

        if (phoneNumber.isNotBlank() && phoneNumber.length != 10) {
            phoneError = "Phone number must be 10 digits"
            isValid = false
        } else {
            phoneError = null
        }

        if (aadhaarNumber.isNotBlank() && aadhaarNumber.length != 12) {
            aadhaarError = "Aadhaar number must be 12 digits"
            isValid = false
        } else {
            aadhaarError = null
        }

        if (amount.isBlank() || amount.toDoubleOrNull() == null || amount.toDouble() <= 0.0) {
            amountError = "Valid amount is required"
            isValid = false
        } else {
            amountError = null
        }

        if (quantity.isBlank() || quantity.toIntOrNull() == null || quantity.toInt() <= 0) {
            quantityError = "Valid quantity is required"
            isValid = false
        } else {
            quantityError = null
        }

        // Date validation (no future dates)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.parse(sdf.format(Date()))
        val selected = kotlin.runCatching { sdf.parse(transactionDate) }.getOrNull()
        if (selected == null || selected.after(today)) {
            dateError = "Date cannot be in the future"
            isValid = false
        } else {
            dateError = null
        }

        // Transaction type validation (inventory-based)
        val qty = quantity.toIntOrNull() ?: 0
        val item = inventoryRepo.getItemBySerial(serialNumber)
        when (selectedTransactionType) {
            "Sale" -> {
                if (item == null) {
                    serialError = "Serial not found in inventory"
                    isValid = false
                } else if (item.quantity < qty) {
                    quantityError = "Insufficient quantity available"
                    isValid = false
                }
            }
            "Purchase" -> {
                if (item != null) {
                    serialError = "Serial already exists in inventory"
                    isValid = false
                }
            }
            "Repair" -> {
                if (item == null) {
                    serialError = "Serial not found in inventory"
                    isValid = false
                }
            }
            "Return" -> {
                val wasSold = inventoryRepo.wasSerialSold(serialNumber)
                if (!wasSold) {
                    serialError = "Serial not sold previously"
                    isValid = false
                }
            }
        }
        return isValid
    }

    suspend fun updateInventoryAfterTransaction(
        transactionType: String,
        serial: String,
        model: String,
        qty: Int,
        phone: String,
        aadhaar: String,
        description: String,
        images: List<String>
    ) {
        val existingItem = inventoryRepo.getItemBySerial(serial)
        when (transactionType) {
            "Purchase" -> {
                if (existingItem == null) {
                    val newItem = InventoryItem(
                        serial = serial,
                        name = model,
                        model = model,
                        quantity = qty,
                        phone = phone,
                        aadhaar = aadhaar,
                        description = description,
                        date = transactionDate,
                        timestamp = System.currentTimeMillis(),
                        imageUrls = images
                    )
                    inventoryRepo.addOrUpdateItem(serial, newItem)
                } else {
                    val updatedItem = existingItem.copy(quantity = existingItem.quantity + qty)
                    inventoryRepo.addOrUpdateItem(serial, updatedItem)
                }
            }
            "Sale" -> {
                if (existingItem != null) {
                    val updatedQty = existingItem.quantity - qty
                    val updatedItem = existingItem.copy(quantity = updatedQty.coerceAtLeast(0))
                    inventoryRepo.addOrUpdateItem(serial, updatedItem)
                }
            }
            // You can add more logic for "Return" and "Repair" if desired
        }
    }

    fun submitTransaction() {
        if (!validateForm()) return

        scope.launch {
            loading = true
            try {
                val transaction = Transaction(
                    id = "",
                    serial = serialNumber,
                    model = modelName,
                    type = selectedTransactionType,
                    customerName = customerName,
                    phoneNumber = phoneNumber.ifBlank { "" },
                    aadhaarNumber = aadhaarNumber.ifBlank { "" },
                    amount = amount.toDouble(),
                    quantity = quantity.toInt(),
                    description = description.ifBlank { "" },
                    date = transactionDate,
                    timestamp = System.currentTimeMillis(),
                    userRole = userRole.name,
                    images = selectedImages.map { it.toString() }
                )

                val result = inventoryRepo.addTransaction(serialNumber, transaction)
                if (result is com.example.inventoryapp.data.Result.Success) {
                    updateInventoryAfterTransaction(
                        transactionType = selectedTransactionType,
                        serial = serialNumber,
                        model = modelName,
                        qty = quantity.toInt(),
                        phone = phoneNumber.ifBlank { "" },
                        aadhaar = aadhaarNumber.ifBlank { "" },
                        description = description.ifBlank { "" },
                        images = selectedImages.map { it.toString() }
                    )
                    snackbarHostState.showSnackbar("Transaction & Inventory updated successfully")
                    navController.popBackStack()
                } else {
                    snackbarHostState.showSnackbar("Failed to save transaction")
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error: ${e.message}")
            } finally {
                loading = false
            }
        }
    }

    // Date Picker Dialog
    if (datePickerDialogOpen) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = sdf.parse(sdf.format(Date()))
                val selected = kotlin.runCatching { sdf.parse(selectedDate) }.getOrNull()
                if (selected != null && !selected.after(today)) {
                    transactionDate = selectedDate
                    dateError = null
                } else {
                    dateError = "Date cannot be in the future"
                }
                datePickerDialogOpen = false
            },
            year, month, day
        ).apply {
            datePicker.maxDate = calendar.timeInMillis
        }.show()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Transaction") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transaction Type Selection (Segmented Card UI)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Transaction Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val transactionTypes = listOf("Sale", "Purchase", "Return", "Repair")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        transactionTypes.forEach { type ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selectedTransactionType == type) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                    .clickable { selectedTransactionType = type },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedTransactionType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .padding(vertical = 10.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        type,
                                        color = if (selectedTransactionType == type) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Serial Number & Model
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Item Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                navToBarcodeScanner?.invoke() ?: navController.navigate("barcode_scanner")
                            }
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = serialNumber,
                        onValueChange = {
                            serialNumber = it
                            serialError = null
                        },
                        label = { Text("Serial Number") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = serialError != null,
                        supportingText = serialError?.let { { Text(it) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = modelName,
                        onValueChange = {
                            modelName = it
                            modelError = null
                        },
                        label = { Text("Model Name") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = modelError != null,
                        supportingText = modelError?.let { { Text(it) } }
                    )
                }
            }

            // Customer Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Customer Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = {
                            customerName = it
                            customerNameError = null
                        },
                        label = { Text("Customer Name") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = customerNameError != null,
                        supportingText = customerNameError?.let { { Text(it) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = {
                            if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                                phoneNumber = it
                                phoneError = null
                            }
                        },
                        label = { Text("Phone Number (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = phoneError != null,
                        supportingText = phoneError?.let { { Text(it) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = aadhaarNumber,
                        onValueChange = {
                            if (it.length <= 12 && it.all { char -> char.isDigit() }) {
                                aadhaarNumber = it
                                aadhaarError = null
                            }
                        },
                        label = { Text("Aadhaar Number (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = aadhaarError != null,
                        supportingText = aadhaarError?.let { { Text(it) } }
                    )
                }
            }

            // Transaction Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = {
                                amount = it
                                amountError = null
                            },
                            label = { Text("Amount") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = amountError != null,
                            supportingText = amountError?.let { { Text(it) } }
                        )

                        OutlinedTextField(
                            value = quantity,
                            onValueChange = {
                                quantity = it
                                quantityError = null
                            },
                            label = { Text("Quantity") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = quantityError != null,
                            supportingText = quantityError?.let { { Text(it) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = transactionDate,
                        onValueChange = {},
                        label = { Text("Date") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialogOpen = true },
                        readOnly = true,
                        isError = dateError != null,
                        supportingText = dateError?.let { { Text(it) } }
                    )
                }
            }

            // Image Upload Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Images (${selectedImages.size}/5)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                enabled = selectedImages.size < 5
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                            }
                            IconButton(
                                onClick = {
                                    val uri = createImageUri()
                                    cameraImageUri = uri
                                    cameraLauncher.launch(uri)
                                },
                                enabled = selectedImages.size < 5
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                            }
                        }
                    }

                    if (selectedImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedImages) { uri ->
                                Box {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = {
                                            selectedImages = selectedImages.filter { it != uri }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.6f),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Submit Button
            Button(
                onClick = { submitTransaction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !loading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Transaction", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}