package com.example.inventoryapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavController

// Assume you use MLKit or ZXing for barcode scanning.
// Here is a placeholder Compose screen that handles permission and returns result.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    navController: NavController
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    // Placeholder for scanned result (replace with your actual scanner integration)
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            scanning = true
        }
    }

    fun onBarcodeScanned(result: String) {
        // Pass result back to previous screen using SavedStateHandle
        navController.previousBackStackEntry?.savedStateHandle?.set("scannedSerial", result)
        navController.popBackStack()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            !hasCameraPermission -> {
                Text("Camera permission is required to scan barcodes.")
            }
            scanning -> {
                // Replace this Column with your camera/scanner composable!
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pretend barcode scanner running...")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        // Simulate a barcode scan result for testing
                        onBarcodeScanned("SN1234567890")
                    }) {
                        Text("Simulate Scan (SN1234567890)")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { navController.popBackStack() }) {
                        Text("Cancel")
                    }
                }
            }
            scanError != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: $scanError", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { navController.popBackStack() }) {
                        Text("Back")
                    }
                }
            }
        }
    }
}