package com.example.inventoryapp.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

fun downloadImage(
    context: Context,
    url: String,
    fileName: String,
    onDownloadComplete: () -> Unit,
    onDownloadError: () -> Unit
) {
    try {
        val input = java.net.URL(url).openStream()
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val file = File(picturesDir, fileName)
        val output = FileOutputStream(file)
        input.use { inp -> output.use { outp -> inp.copyTo(outp) } }
        onDownloadComplete()
    } catch (e: Exception) {
        onDownloadError()
    }
}