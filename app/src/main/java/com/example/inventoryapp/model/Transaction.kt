package com.example.inventoryapp.model

data class Transaction(
    val type: String = "",
    val model: String = "",
    val serial: String = "",
    val phone: String = "",
    val aadhaar: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val date: String = "",          // Now always a String
    val quantity: Int = 1,
    val user: String = "",
    val timestamp: Long = 0L,       // Unix time millis
    val imageUrls: List<String> = emptyList()
)