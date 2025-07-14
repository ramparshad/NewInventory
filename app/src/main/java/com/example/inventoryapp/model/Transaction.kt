package com.example.inventoryapp.model

data class Transaction(
    val id: String = "",
    val type: String = "",
    val model: String = "",
    val serial: String = "",
    val customerName: String = "",
    val phoneNumber: String? = null,
    val aadhaarNumber: String? = null,
    val amount: Double = 0.0,
    val quantity: Int = 1,
    val description: String? = null,
    val date: String = "",
    val timestamp: Long = 0L,
    val userRole: String = "",
    val images: List<String> = emptyList(),
    // Legacy fields for backward compatibility
    val phone: String = phoneNumber ?: "",
    val aadhaar: String = aadhaarNumber ?: "",
    val user: String = userRole,
    val imageUrls: List<String> = images,
    val deletedInfo: DeletedInfo? = null // <--- ADD THIS LINE
)