package com.example.inventoryapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.inventoryapp.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest

data class User(
    val username: String,
    val passwordHash: String,
    val role: UserRole
)

class AuthRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser
    
    // Default users - in production this would be from backend
    private val defaultUsers = listOf(
        User("admin", hashPassword("admin123"), UserRole.ADMIN),
        User("operator", hashPassword("operator123"), UserRole.OPERATOR),
        User("viewer", hashPassword("viewer123"), UserRole.VIEWER)
    )
    
    init {
        // Initialize default users if not exist
        if (!prefs.getBoolean("users_initialized", false)) {
            defaultUsers.forEach { user ->
                saveUser(user)
            }
            prefs.edit().putBoolean("users_initialized", true).apply()
        }
        
        // Check if user is already logged in
        val savedUsername = prefs.getString("current_user", null)
        if (savedUsername != null) {
            _currentUser.value = getUser(savedUsername)
        }
    }
    
    fun login(username: String, password: String): Result<User> {
        // Allow blank credentials for testing - default to admin //
        if (username.isBlank() && password.isBlank()) {
            val adminUser = defaultUsers.first { it.role == UserRole.ADMIN }
            _currentUser.value = adminUser
            prefs.edit().putString("current_user", adminUser.username).apply()
            return Result.success(adminUser)
        }
        
        val user = getUser(username)
        return if (user != null && user.passwordHash == hashPassword(password)) {
            _currentUser.value = user
            prefs.edit().putString("current_user", username).apply()
            Result.success(user)
        } else {
            Result.failure(Exception("Invalid username or password"))
        }
    }
    
    fun logout() {
        _currentUser.value = null
        prefs.edit().remove("current_user").apply()
    }
    
    fun getCurrentUserRole(): UserRole? = _currentUser.value?.role
    
    fun isLoggedIn(): Boolean = _currentUser.value != null
    
    fun canEdit(): Boolean = getCurrentUserRole() in listOf(UserRole.ADMIN, UserRole.OPERATOR)
    
    fun canDelete(): Boolean = getCurrentUserRole() == UserRole.ADMIN
    
    fun canViewAnalytics(): Boolean = getCurrentUserRole() == UserRole.ADMIN
    
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }
    
    fun authenticateWithBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isBiometricAvailable()) {
            onError("Biometric authentication not available")
            return
        }
        
        val lastUsername = prefs.getString("last_biometric_user", null)
        if (lastUsername == null) {
            onError("No biometric user registered")
            return
        }
        
        val biometricPrompt = BiometricPrompt(activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val user = getUser(lastUsername)
                    if (user != null) {
                        _currentUser.value = user
                        prefs.edit().putString("current_user", lastUsername).apply()
                        onSuccess()
                    } else {
                        onError("User not found")
                    }
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed")
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Use your fingerprint to login")
            .setNegativeButtonText("Cancel")
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    fun enableBiometricForUser(username: String) {
        prefs.edit().putString("last_biometric_user", username).apply()
    }
    
    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
    
    private fun saveUser(user: User) {
        prefs.edit()
            .putString("user_${user.username}_password", user.passwordHash)
            .putString("user_${user.username}_role", user.role.name)
            .apply()
    }
    
    private fun getUser(username: String): User? {
        val passwordHash = prefs.getString("user_${username}_password", null)
        val roleString = prefs.getString("user_${username}_role", null)
        
        return if (passwordHash != null && roleString != null) {
            User(username, passwordHash, UserRole.valueOf(roleString))
        } else null
    }
}