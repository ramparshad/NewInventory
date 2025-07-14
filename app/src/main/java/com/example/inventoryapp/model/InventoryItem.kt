package com.example.inventoryapp.model

/**
 * Represents a single inventory item in the Firestore database.
 * Now supports multiple images per item.
 */
data class InventoryItem(
    val serial: String = "",                    // Unique serial number (document ID)
    val name: String = "",                      // Item name or description
    val model: String = "",                     // Model (required, used for analytics/filter)
    val quantity: Int = 0,                      // Current quantity in stock
    val phone: String = "",                     // Optional: associated phone number
    val aadhaar: String = "",                   // Optional: associated Aadhaar number
    val description: String = "",               // Optional: notes or description
    val date: String = "",                      // Creation/purchase date ("yyyy-MM-dd")
    val timestamp: Long = 0L,                   // Unix time in millis for sorting/filtering
    val imageUrls: List<String> = emptyList(),   // Supports multiple images per item
    val isSold: Boolean = false,
    val isInRepair: Boolean = false
)