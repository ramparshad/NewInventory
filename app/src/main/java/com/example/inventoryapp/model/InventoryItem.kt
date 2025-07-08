package com.example.inventoryapp.model

data class InventoryItem(
    val serial: String = "",
    val name: String = "",
    val model: String = "",
    val quantity: Int = 0,
    val phone: String = "",
    val aadhaar: String = "",
    val description: String = "",
    val date: String = "",          // Now always a String (e.g., "yyyy-MM-dd")
    val timestamp: Long = 0L,       // Unix time millis, for sorting/filtering
    val imageUrl: String? = null
)