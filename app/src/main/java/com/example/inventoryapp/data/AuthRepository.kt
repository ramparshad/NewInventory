package com.example.inventoryapp.data

import com.example.inventoryapp.model.UserRole

class AuthRepository {
    // Always return ADMIN for now; replace with real login logic later
    fun getCurrentUserRole(): UserRole = UserRole.ADMIN
}