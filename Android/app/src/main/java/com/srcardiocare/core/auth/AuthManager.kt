// AuthManager.kt — Firebase Auth manager for Android
package com.srcardiocare.core.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.srcardiocare.core.security.PasswordValidator
import com.srcardiocare.core.security.SecurePreferences

/**
 * Manages authentication state using Firebase Auth.
 * Firebase handles token management automatically (1hr ID tokens, auto-refresh).
 * Local SharedPreferences caches the user role for quick access.
 */
class AuthManager(context: Context) {

    private val prefs: SharedPreferences = SecurePreferences.getInstance(context)

    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val KEY_USER_ROLE = "user_role"
    }

    var userRole: String?
        get() = prefs.getString(KEY_USER_ROLE, null)
        set(value) = prefs.edit().putString(KEY_USER_ROLE, value).apply()

    val isLoggedIn: Boolean get() = auth.currentUser != null

    val currentUID: String? get() = auth.currentUser?.uid

    fun clearAll() {
        prefs.edit().clear().apply()
        auth.signOut()
    }

    /**
     * Validates password meets minimum requirements:
     * 8+ characters, at least one uppercase, one lowercase, one digit.
     * @deprecated Use PasswordValidator.validate() instead for consistent validation
     */
    @Deprecated(
        message = "Use PasswordValidator.validate() instead",
        replaceWith = ReplaceWith("PasswordValidator.validate(password).isValid")
    )
    fun isValidPassword(password: String): Boolean = PasswordValidator.validate(password).isValid
}
