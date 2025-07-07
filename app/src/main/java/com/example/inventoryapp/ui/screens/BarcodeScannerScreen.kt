package com.example.inventoryapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

@Composable
fun BarcodeScannerScreen(
    navController: NavController,
    overlayBoxColor: Color = Color(0x3300FF00),
    onBarcodeScanned: (String) -> Unit = { scannedCode ->
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set("scannedSerial", scannedCode)
        navController.popBackStack()
    }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(true) }
    var lastScanned by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    // Ask for camera permission
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                (context as android.app.Activity),
                arrayOf(Manifest.permission.CAMERA),
                10
            )
        }
        permissionDenied =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
    }

    Box(Modifier.fillMaxSize()) {
        if (permissionDenied) {
            Text("Camera permission denied. Please enable it in settings.", color = Color.Red)
            return@Box
        }
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val scanner = BarcodeScanning.getClient()
                    val imageAnalyzer = ImageAnalysis.Builder()
                        // CameraX recommends using aspect ratio instead of setTargetResolution
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                ContextCompat.getMainExecutor(ctx)
                            ) { imageProxy ->
                                if (!scanning) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val code = barcodes.firstOrNull { it.rawValue != null }
                                            if (code != null && code.rawValue != lastScanned) {
                                                scanning = false
                                                lastScanned = code.rawValue
                                                onBarcodeScanned(code.rawValue ?: "")
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            errorMsg = e.localizedMessage
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        errorMsg = e.localizedMessage
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        // Scanning box overlay
        Box(
            Modifier
                .align(Alignment.Center)
                .size(250.dp, 150.dp)
                .background(overlayBoxColor, RoundedCornerShape(24.dp))
        )
        // Border for the scanning box
        Box(
            Modifier
                .align(Alignment.Center)
                .size(250.dp, 150.dp)
                .border(3.dp, Color.Green, RoundedCornerShape(24.dp))
        )
        // Error message
        errorMsg?.let {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Red.copy(alpha = 0.8f))
            ) {
                Text(
                    text = it,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        // Visual feedback
        lastScanned?.let {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color(0x9900FF00), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Scanned: $it",
                    color = Color.Black,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}