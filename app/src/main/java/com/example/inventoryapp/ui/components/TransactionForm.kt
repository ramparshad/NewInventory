package com.example.inventoryapp.ui.components

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.inventoryapp.data.InventoryRepository
import com.example.inventoryapp.data.Result
import com.example.inventoryapp.model.Transaction
import com.example.inventoryapp.model.UserRole
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
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val savedState = navController.currentBackStackEntry?.savedStateHandle

    val transactionTypes = listOf("Purchase", "Sale", "Return", "Repair")
    var type by remember { mutableStateOf(prefillType ?: transactionTypes.first()) }
    val serialStack = remember { mutableStateListOf<String>() }
    val modelStack = remember { mutableStateListOf<String>() }
    var serial by remember { mutableStateOf(prefillSerial ?: "") }
    var model by remember { mutableStateOf(prefillModel ?: "") }
    var isModelAuto by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var quantity by remember { mutableStateOf("1") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    val toolTips = mapOf(
        "serial" to "Unique code for the item (scan or enter manually)",
        "model" to "Product model (auto-filled if available)",
        "phone" to "Customer phone (optional)",
        "aadhaar" to "Customer Aadhaar (optional)",
        "amount" to "Transaction value",
        "description" to "Additional details",
        "date" to "Date of transaction",
        "quantity" to "Number of units"
    )

    var serialError by remember { mutableStateOf<String?>(null) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var quantityError by remember { mutableStateOf<String?>(null) }

    val serialFocus = remember { FocusRequester() }
    val modelFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val aadhaarFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    val quantityFocus = remember { FocusRequester() }

    fun pushToStack(stack: MutableList<String>, value: String) {
        if (stack.isEmpty() || stack.last() != value) stack.add(value)
    }
    fun popFromStack(stack: MutableList<String>, current: String): String =
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex).also { if (stack.isEmpty()) stack.add(current) } else current

    var modelSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(model) {
        if (model.isNotBlank()) {
            val models = inventoryRepo.getAllModels().toList()
            val suggestions = models.filter { it.contains(model, ignoreCase = true) }.take(5)
            modelSuggestions = suggestions
        } else {
            modelSuggestions = emptyList()
        }
    }

    val scanHistory = remember { mutableStateListOf<String>() }
    var bulkScanMode by remember { mutableStateOf(false) }
    val scannedSerials = remember { mutableStateListOf<String>() }

    // FIXED: use PickVisualMediaRequest for the launcher!
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        images = uris?.take(5) ?: emptyList()
    }
    val singleImgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            images = images + it
        }
    }

    LaunchedEffect(savedState?.get<String>("scannedSerial")) {
        savedState?.get<String>("scannedSerial")?.let { code ->
            serial = code
            scanHistory.add(0, code)
            if (bulkScanMode) scannedSerials.add(code)
            savedState.remove<String>("scannedSerial")
        }
    }

    LaunchedEffect(serial, type) {
        if (serial.isNotBlank() && type != "Purchase") {
            scope.launch(Dispatchers.IO) {
                val item = inventoryRepo.getItemBySerial(serial)
                if (item != null && item.quantity > 0) {
                    model = item.model
                    isModelAuto = true
                } else {
                    model = ""
                    isModelAuto = false
                }
            }
        } else if (type == "Purchase") {
            isModelAuto = false
        }
    }

    fun isDateValid(selected: Calendar): Boolean = !selected.after(Calendar.getInstance())
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
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .padding(24.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SegmentedButton(
                options = transactionTypes,
                selected = type,
                onSelected = { type = it }
            )
            Spacer(Modifier.height(4.dp))

            if (bulkScanMode) {
                Text("Bulk scan mode: Scan items in sequence", color = Color.Blue)
                if (scannedSerials.isNotEmpty()) {
                    Text("Scanned: ${scannedSerials.joinToString()}")
                }
            }

            OutlinedTextField(
                value = serial,
                onValueChange = {
                    pushToStack(serialStack, serial)
                    serial = it
                    serialError = null
                    isModelAuto = false
                },
                label = { Text("Serial Number") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(serialFocus),
                singleLine = true,
                trailingIcon = {
                    Row {
                        IconButton(onClick = { navController.navigate("barcode_scanner") }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Barcode")
                        }
                        if (serialStack.isNotEmpty()) {
                            IconButton(onClick = { serial = popFromStack(serialStack, serial) }) {
                                Text("Undo")
                            }
                        }
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
            serialError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = model,
                onValueChange = {
                    if (!isModelAuto) {
                        pushToStack(modelStack, model)
                        model = it
                    }
                    modelError = null
                },
                label = { Text("Model") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(modelFocus),
                singleLine = true,
                enabled = !isModelAuto && type == "Purchase" && canEdit && !loading,
                isError = modelError != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { phoneFocus.requestFocus() }
                ),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (modelStack.isNotEmpty()) {
                        IconButton(onClick = { model = popFromStack(modelStack, model) }) {
                            Text("Undo")
                        }
                    }
                }
            )
            modelError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            DropdownMenu(
                expanded = modelSuggestions.isNotEmpty(),
                onDismissRequest = { modelSuggestions = emptyList() }
            ) {
                modelSuggestions.forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            model = it
                            modelSuggestions = emptyList()
                        }
                    )
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
            amountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

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
                onClick = {
                    val calendar = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val picked = Calendar.getInstance()
                            picked.set(year, month, dayOfMonth)
                            if (isDateValid(picked)) {
                                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(picked.time)
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Future dates are not allowed.")
                                }
                            }
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                enabled = canEdit && !loading,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (date.isBlank()) "Pick Date" else date)
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
            quantityError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // FIXED: pass PickVisualMediaRequest for image picking
            Button(
                onClick = {
                    singleImgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = images.size < 5 && canEdit && !loading
            ) {
                Text("Pick Image")
            }

            Button(
                onClick = {
                    imgPicker.launch(null) // For multiple images
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = images.size < 5 && canEdit && !loading
            ) {
                Text("Pick Multiple Images")
            }

            if (images.isNotEmpty()) {
                Column {
                    Text("Tap an image to remove")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        images.forEach { uri ->
                            Image(
                                painter = rememberAsyncImagePainter(model = uri),
                                contentDescription = "Selected image",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = canEdit && !loading) {
                                        images = images - uri
                                    }
                            )
                        }
                    }
                }
            }

            if (scanHistory.isNotEmpty()) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Recent Scans:", color = Color.Gray)
                    scanHistory.take(5).forEach {
                        Text(it, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    // Validate fields
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
                        scope.launch { snackbarHostState.showSnackbar("Date is required") }
                        valid = false
                    }
                    if (!valid) return@Button

                    loading = true

                    scope.launch {
                        try {
                            val imageUrls = mutableListOf<String>()
                            if (images.isNotEmpty()) {
                                val storage = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                                for ((index, uri) in images.withIndex()) {
                                    val ref = storage.child("transactions/${serial}_${System.currentTimeMillis()}_${index}.jpg")
                                    ref.putFile(uri).await()
                                    imageUrls += ref.downloadUrl.await().toString()
                                }
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
                                type = type
                            )
                            val item = inventoryRepo.getItemBySerial(serial)
                            if (type == "Sale" && (item == null || item.quantity < 1)) {
                                snackbarHostState.showSnackbar("Cannot sell: item not in inventory or out of stock.")
                                loading = false
                                return@launch
                            } else if (type == "Purchase" && item != null) {
                                snackbarHostState.showSnackbar("Cannot purchase: serial already exists.")
                                loading = false
                                return@launch
                            }
                            val result = inventoryRepo.addTransaction(serial, transaction)
                            loading = false
                            if (result is Result.Success) {
                                showSuccess.value = true
                                snackbarHostState.showSnackbar("Transaction saved successfully!")
                                serial = ""
                                model = ""
                                isModelAuto = false
                                phone = ""
                                aadhaar = ""
                                amount = ""
                                description = ""
                                quantity = "1"
                                images = emptyList()
                            } else if (result is Result.Error) {
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
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp)),
                enabled = canEdit && !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
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