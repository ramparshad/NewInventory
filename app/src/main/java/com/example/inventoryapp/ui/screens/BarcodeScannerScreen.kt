package com.example.inventoryapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scanResult by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            !hasCameraPermission -> {
                Text("Camera permission is required to scan barcodes.")
            }
            else -> {
                CameraPreview(
                    onBarcodeScanned = { code ->
                        if (scanResult == null) {
                            scanResult = code
                            navController.previousBackStackEntry?.savedStateHandle?.set("scannedSerial", code)
                            navController.popBackStack()
                        }
                    },
                    onError = { error = it },
                    torchEnabled = torchEnabled,
                    onTorchChanged = { torchEnabled = it },
                    lifecycleOwner = lifecycleOwner
                )

                // Torch toggle and cancel button
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row {
                        Button(onClick = { torchEnabled = !torchEnabled }) {
                            Text(if (torchEnabled) "Torch Off" else "Torch On")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = { navController.popBackStack() }) {
                            Text("Cancel")
                        }
                    }
                }
                error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun CameraPreview(
    onBarcodeScanned: (String) -> Unit,
    onError: (String) -> Unit,
    torchEnabled: Boolean,
    onTorchChanged: (Boolean) -> Unit,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var camera: Camera? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder()
            .setTargetResolution(Size(1280, 720))
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val barcodeScanner = BarcodeScanning.getClient()
        val analysisUseCase = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysisUseCase.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { code ->
                                onBarcodeScanned(code)
                                break
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        onError(e.message ?: "Barcode scan failed")
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, analysisUseCase
            )
            camera?.cameraControl?.enableTorch(torchEnabled)
        } catch (e: Exception) {
            onError(e.message ?: "Camera initialization failed")
        }

        onDispose {
            cameraProvider.unbindAll()
        }
    }

    // React to torch state changes
    LaunchedEffect(torchEnabled) {
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    AndroidView(
        factory = {
            previewView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}