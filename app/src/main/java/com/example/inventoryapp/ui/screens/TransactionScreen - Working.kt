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
    var modelName by remember { mutableStateOf(prefillModel ?: "") }
    var customerName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var aadhaarNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Field errors
    var serialError by remember { mutableStateOf<String?>(null) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var customerNameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var aadhaarError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var quantityError by remember { mutableStateOf<String?>(null) }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.size <= 5) {
            selectedImages = uris
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // Handle camera result
        }
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

        if (amount.isBlank() || amount.toDoubleOrNull() == null) {
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

        // Inventory validation
        // (pseudo-code, to be implemented with repository methods)
        // val serialExists = inventoryRepo.serialExists(serialNumber)
        // val transactionAllowed = when (selectedTransactionType) {
        //     "Sale" -> serialExists
        //     "Purchase" -> !serialExists
        //     "Repair" -> serialExists
        //     "Return" -> inventoryRepo.wasSoldPreviously(serialNumber)
        //     else -> true
        // }
        // if (!transactionAllowed) {
        //     serialError = "This transaction is not allowed for the current inventory state."
        //     isValid = false
        // }

        return isValid
    }

    fun submitTransaction() {
        if (!validateForm()) return

        scope.launch {
            try {
                loading = true

                val transaction = Transaction(
                    id = "", // Will be generated by Firestore
                    serial = serialNumber,
                    model = modelName,
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
                    images = selectedImages.map { it.toString() }
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
            // Transaction Type Selection
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
                            FilterChip(
                                onClick = { selectedTransactionType = type },
                                label = { Text(type) },
                                selected = selectedTransactionType == type,
                                modifier = Modifier.weight(1f)
                            )
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
                                onClick = { /* TODO: Implement camera */ },
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