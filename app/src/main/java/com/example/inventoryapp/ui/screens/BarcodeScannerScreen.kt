package com.example.inventoryapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Camera permission is required to scan barcodes.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
            else -> {
                // Camera preview
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

                // Barcode scanning overlay
                ScannerOverlay()

                // Top instruction bar
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = "Align IMEI barcode or serial number within the frame",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Bottom controls
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { torchEnabled = !torchEnabled },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (torchEnabled) "Flash Off" else "Flash On")
                        }
                        Button(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                // Error message
                error?.let { 
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Error: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
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

    DisposableEffect(torchEnabled) {
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val barcodeScanner = BarcodeScanning.getClient()
        val analysisUseCase = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysisUseCase.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        // Filter for IMEI-like codes (15 digits) or any barcode with valid serial format
                        val validCode = barcodes.firstOrNull { barcode ->
                            val code = barcode.rawValue
                            when {
                                // IMEI: exactly 15 digits
                                code?.matches(Regex("^\\d{15}$")) == true -> true
                                // Serial number: alphanumeric, 6-20 characters
                                code?.matches(Regex("^[A-Za-z0-9]{6,20}$")) == true -> true
                                // Other barcode formats that might contain serial numbers
                                code?.length in 8..25 -> true
                                else -> false
                            }
                        }?.rawValue
                        
                        if (validCode != null) {
                            onBarcodeScanned(validCode)
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

@Composable
fun ScannerOverlay() {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val scannerSize = minOf(canvasWidth, canvasHeight) * 0.6f
        val left = (canvasWidth - scannerSize) / 2
        val top = (canvasHeight - scannerSize) / 2
        val right = left + scannerSize
        val bottom = top + scannerSize

        // Darken the area outside the scanner frame
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            size = size
        )

        // Create a clear rectangle in the center
        drawRect(
            color = Color.Transparent,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(scannerSize, scannerSize)
        )

        // Draw corner indicators
        val cornerLength = 30f
        val cornerStroke = 4f
        val cornerColor = Color.White

        // Top-left corner
        drawLine(
            color = cornerColor,
            start = androidx.compose.ui.geometry.Offset(left, top),
            end = androidx.compose.ui.geometry.Offset(left + cornerLength, top),
            strokeWidth = cornerStroke
        )
        drawLine(
            color = cornerColor,
            start = androidx.compose.ui.geometry.Offset(left, top),
            end = androidx.compose.ui.geometry.Offset(left, top + cornerLength),
            strokeWidth = cornerStroke
        )

        // Top-right corner
        drawLine(
            color = cornerColor,
            start = androidx.compose.ui.geometry.Offset(right, top),
            end = androidx.compose.ui.geometry.Offset(right - cornerLength, top),
            strokeWidth = cornerStroke
        )
        drawLine(
            color = cornerColor,
            start = androidx.compose.ui.geometry.Offset(right, top),
            end = androidx.compose.ui.geometry.Offset(right, top + cornerLength),
            strokeWidth = cornerStroke
        )

        // Bottom-left corner
        drawLine(
            color = cornerColor,
            start = androidx.compose.ui.geometry.Offset(left, bottom),
            end = androidx.compose.ui.geometry.Offset(left + cornerLength, bottom),
            strokeWidth = cornerStroke
        )
        drawLine(
            color = cornerColor,
            start = androidx.compose.ui.geometry.Offset(left, bottom),
            end = androidx.compose.ui.geometry.Offset(left, bottom - cornerLength),
            strokeWidth = cornerStroke
        )

        // Bottom-right corner
        drawLine(
            color = cornerColor,
            start = androidx.compose.ui.geometry.Offset(right, bottom),
            end = androidx.compose.ui.geometry.Offset(right - cornerLength, bottom),
            strokeWidth = cornerStroke
        )
        drawLine(
            color = cornerColor,
            start = androidx.compose.ui.geometry.Offset(right, bottom),
            end = androidx.compose.ui.geometry.Offset(right, bottom - cornerLength),
            strokeWidth = cornerStroke
        )
    }
}