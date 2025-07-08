package com.example.inventoryapp.model

data class InventoryItem(
    val serial: String = "",
    val name: String = "",         // Human-readable name
    val model: String = "",
    val quantity: Int = 0,         // Inventory stock count
    val phone: String = "",
    val aadhaar: String = "",
    val description: String = "",
    val date: Long = 0L,
    val timestamp: Long = 0L,
    val imageUrl: String? = null
)