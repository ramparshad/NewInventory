package com.example.inventoryapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var item by remember { mutableStateOf<InventoryItem?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Form fields
    var serialNumber by remember { mutableStateOf(prefillSerial ?: "") }
    var customerName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var aadhaarNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    
    // Field errors
    var serialError by remember { mutableStateOf<String?>(null) }
    var customerNameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var aadhaarError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var quantityError by remember { mutableStateOf<String?>(null) }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        selectedImages = uris
    }
    
    // Camera launcher for taking photos
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // Handle camera capture success - would need to create URI first
        }
    }

    // Transaction types
    val transactionTypes = listOf("Sale", "Purchase", "Return", "Repair")

    // Load item details when serial changes
    LaunchedEffect(serialNumber) {
        if (serialNumber.isNotBlank()) {
            loading = true
            try {
                item = inventoryRepo.getItemBySerial(serialNumber)
                error = if (item == null) "Item not found" else null
            } catch (e: Exception) {
                error = "Error loading item: ${e.message}"
                item = null
            } finally {
                loading = false
            }
        } else {
            item = null
            error = null
        }
    }

    // Validation functions
    fun validateFields(): Boolean {
        var isValid = true
        
        // Serial validation
        if (serialNumber.isBlank()) {
            serialError = "Serial number is required"
            isValid = false
        } else {
            serialError = null
        }
        
        // Customer name validation
        if (customerName.isBlank()) {
            customerNameError = "Customer name is required"
            isValid = false
        } else {
            customerNameError = null
        }
        
        // Phone validation (10 digits)
        if (phoneNumber.isNotBlank() && !phoneNumber.matches(Regex("^\\d{10}$"))) {
            phoneError = "Phone number must be 10 digits"
            isValid = false
        } else {
            phoneError = null
        }
        
        // Aadhaar validation (12 digits)
        if (aadhaarNumber.isNotBlank() && !aadhaarNumber.matches(Regex("^\\d{12}$"))) {
            aadhaarError = "Aadhaar number must be 12 digits"
            isValid = false
        } else {
            aadhaarError = null
        }
        
        // Amount validation
        if (amount.isBlank()) {
            amountError = "Amount is required"
            isValid = false
        } else {
            try {
                amount.toDouble()
                amountError = null
            } catch (e: NumberFormatException) {
                amountError = "Invalid amount format"
                isValid = false
            }
        }
        
        // Quantity validation
        if (quantity.isBlank()) {
            quantityError = "Quantity is required"
            isValid = false
        } else {
            try {
                val qty = quantity.toInt()
                if (qty <= 0) {
                    quantityError = "Quantity must be positive"
                    isValid = false
                } else {
                    quantityError = null
                }
            } catch (e: NumberFormatException) {
                quantityError = "Invalid quantity format"
                isValid = false
            }
        }
        
        return isValid
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Transaction") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Transaction Type Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Transaction Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transactionTypes) { type ->
                            FilterChip(
                                selected = selectedTransactionType == type,
                                onClick = { selectedTransactionType = type },
                                label = { Text(type) },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Serial Number Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Item Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = serialNumber,
                        onValueChange = { 
                            serialNumber = it
                            serialError = null
                        },
                        label = { Text("Serial Number / IMEI") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { navController.navigate("barcode_scanner") }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                            }
                        },
                        isError = serialError != null,
                        supportingText = serialError?.let { { Text(it) } }
                    )
                    
                    // Show item details if found
                    item?.let { foundItem ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    "Found: ${foundItem.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Model: ${foundItem.model}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Current Stock: ${foundItem.quantity}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    error?.let { errorMsg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Customer Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Customer Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { 
                            customerName = it
                            customerNameError = null
                        },
                        label = { Text("Customer Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        isError = customerNameError != null,
                        supportingText = customerNameError?.let { { Text(it) } }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { 
                            phoneNumber = it
                            phoneError = null
                        },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = phoneError != null,
                        supportingText = phoneError?.let { { Text(it) } }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = aadhaarNumber,
                        onValueChange = { 
                            aadhaarNumber = it
                            aadhaarError = null
                        },
                        label = { Text("Aadhaar Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = aadhaarError != null,
                        supportingText = aadhaarError?.let { { Text(it) } }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Transaction Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Transaction Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { 
                                quantity = it
                                quantityError = null
                            },
                            label = { Text("Quantity *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = quantityError != null,
                            supportingText = quantityError?.let { { Text(it) } }
                        )
                        
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { 
                                amount = it
                                amountError = null
                            },
                            label = { Text("Amount *") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            leadingIcon = { Text("₹", style = MaterialTheme.typography.bodyLarge) },
                            isError = amountError != null,
                            supportingText = amountError?.let { { Text(it) } }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Image Picker Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Attachments (up to 5 images)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Image selection buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                imagePickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedImages.size < 5
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gallery")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                // TODO: Implement camera capture with URI creation
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedImages.size < 5
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Camera")
                        }
                    }
                    
                    // Show selected images
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
                                    
                                    // Remove button
                                    IconButton(
                                        onClick = { 
                                            selectedImages = selectedImages.filter { it != uri }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.7f),
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
            
            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (validateFields()) {
                        scope.launch {
                            try {
                                loading = true
                                val transaction = Transaction(
                                    id = "", // Will be generated by Firestore
                                    serial = serialNumber,
                                    type = selectedTransactionType,
                                    customerName = customerName,
                                    phoneNumber = phoneNumber.ifBlank { null },
                                    aadhaarNumber = aadhaarNumber.ifBlank { null },
                                    amount = amount.toDouble(),
                                    quantity = quantity.toInt(),
                                    description = description.ifBlank { null },
                                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                                    timestamp = System.currentTimeMillis(),
                                    userRole = userRole.name,
                                    images = selectedImages.map { it.toString() } // Store as strings for now
                                )
                                
                                val result = inventoryRepo.addTransaction(serialNumber, transaction)
                                if (result is com.example.inventoryapp.data.Result.Success) {
                                    snackbarHostState.showSnackbar("Transaction saved successfully")
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
                },
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

    // UI State
    var searchText by remember { mutableStateOf("") }
    var filterDialogVisible by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Date") }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Validation Function
    fun isFormValid(): Boolean {
        return (!requiredFields.contains("serial") || serial.isNotBlank()) &&
                (!requiredFields.contains("model") || model.isNotBlank()) &&
                (!requiredFields.contains("amount") || amount.toDoubleOrNull() != null) &&
                (!requiredFields.contains("date") || date.isNotBlank()) &&
                (!requiredFields.contains("type") || type.isNotBlank())
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Search bar at the top
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { filterDialogVisible = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        IconButton(onClick = { navToBarcodeScanner?.invoke() ?: navController.navigate("barcode_scanner") }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode")
                        }
                        Box {
                            TextButton(onClick = { sortMenuExpanded = true }) {
                                Text(sortBy)
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date") },
                                    onClick = { sortBy = "Date"; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Type") },
                                    onClick = { sortBy = "Type"; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Amount") },
                                    onClick = { sortBy = "Amount"; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            if (filterDialogVisible) {
                AlertDialog(
                    onDismissRequest = { filterDialogVisible = false },
                    title = { Text("Filter Options") },
                    text = { Text("Add filter controls here") },
                    confirmButton = {
                        Button(onClick = { filterDialogVisible = false }) { Text("OK") }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Transaction Form UI
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type") },
                    enabled = userRole == UserRole.ADMIN || userRole == UserRole.MANAGER,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serial,
                    onValueChange = { serial = it },
                    label = { Text("Serial") },
                    trailingIcon = {
                        IconButton(onClick = { navToBarcodeScanner?.invoke() ?: navController.navigate("barcode_scanner") }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Serial")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        scope.launch {
                            submitting = true
                            if (!isFormValid()) {
                                snackbarHostState.showSnackbar("Please fill all required fields correctly.")
                                submitting = false
                                return@launch
                            }
                            val doubleAmount = amount.toDoubleOrNull()
                            if (doubleAmount == null) {
                                snackbarHostState.showSnackbar("Please enter a valid amount.")
                                submitting = false
                                return@launch
                            }
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val parsedDate: Long = try {
                                sdf.parse(date)?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }
                            val transaction = Transaction(
                                type = type,
                                serial = serial,
                                model = model,
                                amount = doubleAmount,
                                date = date,
                                timestamp = parsedDate,
                                description = description.ifBlank { "" }
                            )
                            val result = inventoryRepo.addTransaction(serial, transaction)
                            submitting = false
                            if (result is Result.Success) {
                                snackbarHostState.showSnackbar("Transaction added successfully")
                                val bundle = Bundle().apply {
                                    putString("type", type)
                                    putString("serial", serial)
                                    putString("model", model)
                                    putDouble("amount", doubleAmount)
                                    putString("date", date)
                                }
                                firebaseAnalytics.logEvent("transaction_created", bundle)
                                navController.popBackStack()
                            } else {
                                snackbarHostState.showSnackbar(
                                    (result as? Result.Error)?.exception?.message
                                        ?: "Failed to add transaction"
                                )
                            }
                        }
                    },
                    enabled = !submitting && isFormValid(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (submitting) "Submitting..." else "Submit")
                }
            }
        }
    }
}