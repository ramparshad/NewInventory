package com.example.inventoryapp.ui.components

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import coil.compose.rememberAsyncImagePainter
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.InventoryItem
import com.example.inventoryapp.model.Transaction
import com.example.inventoryapp.model.UserRole
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionForm(
    navController: NavController,
    inventoryRepo: InventoryRepository,
    userRole: UserRole,
    requiredFields: List<String>,
    snackbarHostState: SnackbarHostState,
    showSuccess: MutableState<Boolean>,
    prefillType: String? = null,
    prefillSerial: String? = null,
    prefillModel: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val transactionTypes = listOf("Purchase", "Sale", "Return", "Repair")
    var type by remember { mutableStateOf(prefillType ?: transactionTypes.first()) }
    var serial by remember { mutableStateOf(prefillSerial ?: "") }
    var model by remember { mutableStateOf(prefillModel ?: "") }
    var phone by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var quantity by remember { mutableStateOf("1") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    var serialError by remember { mutableStateOf<String?>(null) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var quantityError by remember { mutableStateOf<String?>(null) }
    var imageLimitError by remember { mutableStateOf<String?>(null) }

    val serialFocus = remember { FocusRequester() }
    val modelFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val aadhaarFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    val quantityFocus = remember { FocusRequester() }

    // Model suggestions
    var modelSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(model) {
        if (model.isNotBlank()) {
            val models = inventoryRepo.getAllModels()
            modelSuggestions = models.filter { it.contains(model, ignoreCase = true) }.take(4)
        } else {
            modelSuggestions = emptyList()
        }
    }

    // Permissions state/messages
    var galleryDeniedReason by remember { mutableStateOf<String?>(null) }
    var cameraDeniedReason by remember { mutableStateOf<String?>(null) }

    val maxImages = 10
    var imageSourceSheetOpen by remember { mutableStateOf(false) }

    // Gallery picker launcher
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        uris?.let {
            if (images.size + it.size > maxImages) {
                imageLimitError = "You can select up to $maxImages images per transaction."
            } else {
                images = images + it.take(maxImages - images.size)
                imageLimitError = null
            }
        }
    }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryDeniedReason = null
            imgPicker.launch(androidx.activity.result.PickVisualMediaRequest())
        } else {
            galleryDeniedReason = "Gallery access denied. Please enable permission in the app settings."
            coroutineScope.launch { snackbarHostState.showSnackbar(galleryDeniedReason!!) }
        }
    }

    // Camera launcher/permission
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            if (images.size < maxImages) {
                images = images + cameraImageUri!!
                imageLimitError = null
            } else {
                imageLimitError = "You can select up to $maxImages images per transaction."
            }
        }
    }
    fun createCameraImageUri(): Uri {
        val imagesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val imageFile = java.io.File.createTempFile(
            "transaction_photo_${System.currentTimeMillis()}",
            ".jpg",
            imagesDir
        )
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraDeniedReason = null
            val uri = createCameraImageUri()
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraDeniedReason = "Camera access denied. Please enable permission in the app settings."
            coroutineScope.launch { snackbarHostState.showSnackbar(cameraDeniedReason!!) }
        }
    }

    LaunchedEffect(serial, type) {
        if (serial.isNotBlank() && type != "Purchase") {
            coroutineScope.launch(Dispatchers.IO) {
                val item = inventoryRepo.getItemBySerial(serial)
                if (item != null && item.quantity > 0) {
                    model = item.model
                }
            }
        }
    }

    // Date picker dialog
    var datePickerDialogOpen by remember { mutableStateOf(false) }
    if (datePickerDialogOpen) {
        val calendar = Calendar.getInstance()
        val parts = date.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(y, m, d)
                if (!selectedCal.after(Calendar.getInstance())) {
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedCal.time)
                } else {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Future dates are not allowed.") }
                }
                datePickerDialogOpen = false
            },
            year, month, day
        ).apply {
            datePicker.maxDate = calendar.timeInMillis
        }.show()
    }

    fun formatPhone(input: String) = input.filter { it.isDigit() }.take(10)
    fun formatAadhaar(input: String) = input.filter { it.isDigit() }.take(12)
    val canEdit = userRole == UserRole.ADMIN || userRole == UserRole.STAFF

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFB3CFF2), Color(0xFFFDEB71))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                .padding(24.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "New Transaction",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Transaction type segmented buttons
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                transactionTypes.forEach { t ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clickable { type = t },
                        colors = CardDefaults.cardColors(
                            containerColor = if (type == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            Modifier
                                .padding(vertical = 10.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                t,
                                color = if (type == t) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = serial,
                onValueChange = {
                    serial = it
                    serialError = null
                },
                label = { Text("Serial Number") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(serialFocus),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { navController.navigate("barcode_scanner") }) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = "Scan Barcode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                isError = serialError != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { modelFocus.requestFocus() }
                ),
                enabled = canEdit && !loading,
                shape = RoundedCornerShape(16.dp)
            )
            serialError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = MaterialTheme.typography.bodySmall.fontSize) }

            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    modelError = null
                },
                label = { Text("Model") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(modelFocus),
                singleLine = true,
                enabled = canEdit && !loading,
                isError = modelError != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { phoneFocus.requestFocus() }
                ),
                shape = RoundedCornerShape(16.dp)
            )
            modelError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = MaterialTheme.typography.bodySmall.fontSize) }

            AnimatedVisibility(modelSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F4F8))
                ) {
                    Column {
                        modelSuggestions.forEach {
                            Text(
                                text = it,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        model = it
                                        modelSuggestions = emptyList()
                                    }
                                    .padding(10.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = formatPhone(it) },
                label = { Text("Phone (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(phoneFocus),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { aadhaarFocus.requestFocus() }
                ),
                enabled = canEdit && !loading,
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = aadhaar,
                onValueChange = { aadhaar = formatAadhaar(it) },
                label = { Text("Aadhaar (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(aadhaarFocus),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { amountFocus.requestFocus() }
                ),
                enabled = canEdit && !loading,
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = amount,
                onValueChange = {
                    val filtered = it.filterIndexed { idx, ch -> ch.isDigit() || (ch == '.' && !it.take(idx).contains('.')) }
                    amount = filtered
                    amountError = null
                },
                label = { Text("Amount") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus),
                singleLine = true,
                isError = amountError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { descriptionFocus.requestFocus() }
                ),
                enabled = canEdit && !loading,
                shape = RoundedCornerShape(16.dp)
            )
            amountError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = MaterialTheme.typography.bodySmall.fontSize) }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(descriptionFocus),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { quantityFocus.requestFocus() }
                ),
                enabled = canEdit && !loading,
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedButton(
                onClick = { datePickerDialogOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                enabled = canEdit && !loading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFEAF1FB)
                )
            ) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (date.isBlank()) "Pick Date" else date, color = MaterialTheme.colorScheme.primary)
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = {
                    quantity = it.filter { ch -> ch.isDigit() }
                    quantityError = null
                },
                label = { Text("Quantity") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(quantityFocus),
                singleLine = true,
                isError = quantityError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                enabled = canEdit && !loading,
                shape = RoundedCornerShape(16.dp)
            )
            quantityError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = MaterialTheme.typography.bodySmall.fontSize) }

            Spacer(Modifier.height(10.dp))

            // Merged image source picker button
            OutlinedButton(
                onClick = { imageSourceSheetOpen = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = images.size < maxImages && canEdit && !loading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFFAF8F4)
                )
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Add Images", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("${images.size}/$maxImages", color = Color.Gray)
            }

            // Modal sheet for selecting source
            if (imageSourceSheetOpen) {
                ModalBottomSheet(
                    onDismissRequest = { imageSourceSheetOpen = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    ListItem(
                        headlineContent = { Text("Take Photo") },
                        leadingContent = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                        modifier = Modifier.clickable {
                            imageSourceSheetOpen = false
                            val permission = Manifest.permission.CAMERA
                            val permissionStatus = ContextCompat.checkSelfPermission(context, permission)
                            if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                                cameraDeniedReason = null
                                val uri = createCameraImageUri()
                                cameraImageUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(permission)
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Choose from Gallery") },
                        leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                        modifier = Modifier.clickable {
                            imageSourceSheetOpen = false
                            val permission = Manifest.permission.READ_MEDIA_IMAGES
                            val permissionStatus = ContextCompat.checkSelfPermission(context, permission)
                            if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                                galleryDeniedReason = null
                                imgPicker.launch(androidx.activity.result.PickVisualMediaRequest())
                            } else {
                                galleryPermissionLauncher.launch(permission)
                            }
                        }
                    )
                }
            }

            // Show permission/image errors
            galleryDeniedReason?.let { reason ->
                LaunchedEffect(reason) {
                    snackbarHostState.showSnackbar(reason)
                }
            }
            cameraDeniedReason?.let { reason ->
                LaunchedEffect(reason) {
                    snackbarHostState.showSnackbar(reason)
                }
            }
            imageLimitError?.let { reason ->
                LaunchedEffect(reason) {
                    snackbarHostState.showSnackbar(reason)
                }
            }

            // Image thumbnails
            if (images.isNotEmpty()) {
                Column(Modifier.padding(top = 6.dp, bottom = 6.dp)) {
                    Text("Tap an image to remove", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        images.forEach { uri ->
                            Image(
                                painter = rememberAsyncImagePainter(model = uri),
                                contentDescription = "Selected image",
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF3F3F3))
                                    .clickable(enabled = canEdit && !loading) {
                                        images = images - uri
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    serialError = null
                    modelError = null
                    amountError = null
                    quantityError = null

                    var valid = true
                    if ("serial" in requiredFields && serial.isBlank()) {
                        serialError = "Serial is required"
                        valid = false
                    }
                    if ("model" in requiredFields && model.isBlank()) {
                        modelError = "Model is required"
                        valid = false
                    }
                    val amountDouble = amount.toDoubleOrNull()
                    if ("amount" in requiredFields && amount.isBlank()) {
                        amountError = "Amount is required"
                        valid = false
                    } else if (amountDouble == null || amountDouble <= 0.0) {
                        amountError = "Enter a valid positive number"
                        valid = false
                    }
                    val quantityInt = quantity.toIntOrNull()
                    if ("quantity" in requiredFields && quantity.isBlank()) {
                        quantityError = "Quantity is required"
                        valid = false
                    } else if (quantityInt == null || quantityInt <= 0) {
                        quantityError = "Enter a valid positive number"
                        valid = false
                    }
                    if ("date" in requiredFields && date.isBlank()) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("Date is required") }
                        valid = false
                    }
                    if (!valid) return@Button

                    loading = true

                    coroutineScope.launch {
                        try {
                            val imageUrls = mutableListOf<String>()
                            if (images.isNotEmpty()) {
                                val storage = FirebaseStorage.getInstance().reference
                                for ((index, uri) in images.withIndex()) {
                                    val ref = storage.child("transactions/${serial}_${System.currentTimeMillis()}_${index}.jpg")
                                    ref.putFile(uri).await()
                                    imageUrls += ref.downloadUrl.await().toString()
                                }
                            }

                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val parsedDate: Long = try {
                                sdf.parse(date)?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }

                            val transaction = Transaction(
                                serial = serial,
                                model = model,
                                phone = phone,
                                aadhaar = aadhaar,
                                amount = amountDouble ?: 0.0,
                                description = description,
                                date = date,
                                quantity = quantityInt ?: 1,
                                imageUrls = imageUrls,
                                type = type,
                                timestamp = System.currentTimeMillis()
                            )

                            val item = inventoryRepo.getItemBySerial(serial)
                            val wasSold = inventoryRepo.wasSerialSold(serial)
                            val isInRepair = inventoryRepo.isSerialInRepair(serial) // <-- Implement this in your repo!

                            // Business rules
                            if (type == "Sale" && (item == null || item.quantity < (quantityInt ?: 1))) {
                                snackbarHostState.showSnackbar("Cannot sell: item not in inventory or insufficient stock.")
                                loading = false
                                return@launch
                            }
                            if (type == "Purchase" && item != null) {
                                snackbarHostState.showSnackbar("Cannot purchase: serial already exists in inventory.")
                                loading = false
                                return@launch
                            }
                            if (type == "Repair") {
                                if (item == null) {
                                    snackbarHostState.showSnackbar("Cannot repair: serial not in inventory.")
                                    loading = false
                                    return@launch
                                }
                                // Remove item from inventory (moves to 'in repair')
                                inventoryRepo.removeItemBySerial(serial)
                            }
                            if (type == "Return") {
                                if (!wasSold && !isInRepair) {
                                    snackbarHostState.showSnackbar("Cannot return: item not sold or in repair.")
                                    loading = false
                                    return@launch
                                }
                                // If returning from repair, add back to inventory
                                if (isInRepair) {
                                    val repairedItem = InventoryItem(
                                        serial = serial,
                                        name = model,
                                        model = model,
                                        quantity = quantityInt ?: 1,
                                        phone = phone,
                                        aadhaar = aadhaar,
                                        description = description,
                                        date = date,
                                        timestamp = System.currentTimeMillis(),
                                        imageUrls = imageUrls
                                    )
                                    inventoryRepo.addOrUpdateItem(serial, repairedItem)
                                }
                                // If returning a sold item, handle as per your existing logic (e.g., increase inventory)
                            }

                            val result = inventoryRepo.addTransaction(serial, transaction)
                            if (result is Result.Success) {
                                if (type == "Purchase") {
                                    val newItem = InventoryItem(
                                        serial = serial,
                                        name = model,
                                        model = model,
                                        quantity = quantityInt ?: 1,
                                        phone = phone,
                                        aadhaar = aadhaar,
                                        description = description,
                                        date = date,
                                        timestamp = System.currentTimeMillis(),
                                        imageUrls = imageUrls
                                    )
                                    inventoryRepo.addOrUpdateItem(serial, newItem)
                                }
                                if (type == "Sale" && item != null) {
                                    val updatedQty = item.quantity - (quantityInt ?: 1)
                                    val updatedItem = item.copy(quantity = updatedQty.coerceAtLeast(0))
                                    inventoryRepo.addOrUpdateItem(serial, updatedItem)
                                }

                                loading = false
                                showSuccess.value = true
                                snackbarHostState.showSnackbar("Transaction saved successfully!")
                                serial = ""
                                model = ""
                                phone = ""
                                aadhaar = ""
                                amount = ""
                                description = ""
                                quantity = "1"
                                images = emptyList()
                            } else if (result is Result.Error) {
                                loading = false
                                snackbarHostState.showSnackbar(result.exception?.message ?: "Error saving transaction.")
                            }
                        } catch (e: Exception) {
                            loading = false
                            snackbarHostState.showSnackbar(e.message ?: "Unknown error occurred")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(28.dp)),
                enabled = canEdit && !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Save Transaction", style = MaterialTheme.typography.titleMedium)
            }

            AnimatedVisibility(visible = showSuccess.value) {
                Text(
                    "Transaction successful!",
                    color = Color(0xFF388E3C),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}