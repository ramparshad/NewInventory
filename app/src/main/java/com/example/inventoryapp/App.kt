package com.example.inventoryapp

import android.app.Application
import com.google.firebase.FirebaseApp

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // REMOVE: Log.d("AppInit", "Mock mode: Firebase disabled")
        FirebaseApp.initializeApp(this)
    }
}