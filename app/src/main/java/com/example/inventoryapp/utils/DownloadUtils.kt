package com.example.inventoryapp.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads an image from the given URL and saves it directly to the public Pictures directory (gallery).
 * Handles permissions for Android 13+ (API 33) using READ_MEDIA_IMAGES.
 * Shows real error messages via callback.
 */
suspend fun downloadImageToGallery(
    context: Context,
    url: String,
    fileName: String,
    onDownloadComplete: () -> Unit,
    onDownloadError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        // Permission check for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                onDownloadError("Missing permission: READ_MEDIA_IMAGES")
                return@withContext
            }
        }
        // Permission check for Android < 13 (not strictly needed for MediaStore, but for completeness)
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                onDownloadError("Missing permission: WRITE_EXTERNAL_STORAGE")
                return@withContext
            }
        }

        try {
            // Download from URL
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                onDownloadError("HTTP error: ${connection.responseCode}")
                return@withContext
            }
            val inputStream = connection.inputStream

            // Save to public gallery via MediaStore
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri == null) {
                onDownloadError("Failed to create MediaStore entry")
                return@withContext
            }

            try {
                resolver.openOutputStream(imageUri).use { outputStream ->
                    if (outputStream == null) throw IOException("Failed to open output stream")
                    inputStream.use { inp -> outputStream.use { outp -> inp.copyTo(outp) } }
                }
            } catch (e: Exception) {
                onDownloadError("Failed to write image: ${e.message}")
                return@withContext
            }
            onDownloadComplete()
        } catch (e: Exception) {
            onDownloadError(e.message ?: "Unknown error")
        }
    }
}