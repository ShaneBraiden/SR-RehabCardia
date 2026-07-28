// ErrorHandler.kt — Sanitizes error messages for user display
package com.srcardiocare.core.security

import android.util.Log
import androidx.annotation.StringRes
import com.google.firebase.auth.FirebaseAuthException
import com.srcardiocare.R
import com.srcardiocare.core.locale.LocaleManager

/**
 * Handles error message sanitization to prevent leaking implementation details.
 * Maps technical errors to user-friendly messages.
 *
 * Messages resolve via [LocaleManager.string] so the current language is picked
 * up without every call site having to pass a Context — `getDisplayMessage` is
 * used from ~20 places across patient and doctor screens.
 *
 * Note the map KEYS are Firebase error codes matched against exception text and
 * must never be translated. Only the values are user-facing.
 */
object ErrorHandler {

    private const val TAG = "SRCardiocare"

    /** Falls back to the English text if the Application isn't up yet. */
    private fun str(@StringRes id: Int, fallback: String): String =
        LocaleManager.string(id, fallback)

    // Map of known Firebase/network error patterns to user-friendly messages
    private val errorMappings: Map<String, Int> = mapOf(
        "INVALID_LOGIN_CREDENTIALS" to R.string.error_invalid_credentials,
        "wrong-password" to R.string.error_invalid_credentials,
        "user-not-found" to R.string.error_no_account,
        "email-already-in-use" to R.string.error_email_in_use,
        "weak-password" to R.string.error_weak_password,
        "network-request-failed" to R.string.error_network,
        "too-many-requests" to R.string.error_too_many_requests,
        "user-disabled" to R.string.error_account_disabled,
        "blocked by admin" to R.string.error_account_blocked,
        "account access has been blocked" to R.string.error_account_blocked,
        "invalid-email" to R.string.error_invalid_email,
        "permission-denied" to R.string.error_permission_denied,
        "unavailable" to R.string.error_service_unavailable,
        "PERMISSION_DENIED" to R.string.error_permission_denied,
        "requires-recent-login" to R.string.error_requires_recent_login,
        "credential-already-in-use" to R.string.error_credential_in_use
    )

    /**
     * Android FirebaseAuthException error codes -> user-friendly messages.
     * The Firebase Android SDK reports codes like "ERROR_WRONG_PASSWORD" (not the
     * kebab-case web codes), so these are matched first against [FirebaseAuthException.errorCode].
     */
    private val authCodeMappings: Map<String, Int> = mapOf(
        "ERROR_INVALID_EMAIL" to R.string.error_invalid_email,
        "ERROR_WRONG_PASSWORD" to R.string.error_wrong_password,
        "ERROR_USER_NOT_FOUND" to R.string.error_no_account,
        "ERROR_INVALID_CREDENTIAL" to R.string.error_invalid_credentials,
        "ERROR_USER_DISABLED" to R.string.error_account_disabled,
        "ERROR_USER_TOKEN_EXPIRED" to R.string.error_session_expired,
        "ERROR_TOO_MANY_REQUESTS" to R.string.error_too_many_requests,
        "ERROR_NETWORK_REQUEST_FAILED" to R.string.error_network,
        "ERROR_EMAIL_ALREADY_IN_USE" to R.string.error_email_in_use,
        "ERROR_WEAK_PASSWORD" to R.string.error_weak_password,
        "ERROR_REQUIRES_RECENT_LOGIN" to R.string.error_requires_recent_login
    )

    /**
     * Generic user-friendly messages for different operation types.
     *
     * These are resolved on access rather than being compile-time constants, so
     * they follow the selected language.
     */
    object UserMessages {
        val LOGIN_FAILED get() = str(R.string.error_login_failed, "Login failed. Please try again")
        val REGISTRATION_FAILED get() = str(R.string.error_registration_failed, "Registration failed. Please try again")
        val SAVE_FAILED get() = str(R.string.error_save_failed, "Failed to save. Please try again")
        val LOAD_FAILED get() = str(R.string.error_load_failed, "Failed to load data. Please try again")
        val DELETE_FAILED get() = str(R.string.error_delete_failed, "Failed to delete. Please try again")
        val UPLOAD_FAILED get() = str(R.string.error_upload_failed, "Upload failed. Please try again")
        val NETWORK_ERROR get() = str(R.string.error_network, "Network error. Please check your connection")
        val GENERIC_ERROR get() = str(R.string.error_generic, "An error occurred. Please try again")
        val PASSWORD_CHANGE_FAILED get() = str(R.string.error_password_change_failed, "Failed to change password. Please try again")
    }

    /**
     * Converts an exception to a user-friendly display message.
     * Logs the actual error for debugging but returns a sanitized message.
     *
     * @param exception The exception that occurred
     * @param operationType Description of the operation (e.g., "login", "save patient")
     * @return A user-friendly error message
     */
    fun getDisplayMessage(exception: Exception, operationType: String = "operation"): String {
        // Log the actual error for debugging (never expose to users)
        Log.e(TAG, "Error during $operationType: ${exception.message}", exception)

        val message = exception.message ?: ""

        // Firebase Auth: match the explicit error code first (most reliable on Android).
        if (exception is FirebaseAuthException) {
            authCodeMappings[exception.errorCode]?.let { return str(it, UserMessages.GENERIC_ERROR) }
        }

        // Check for known error patterns
        for ((pattern, messageRes) in errorMappings) {
            if (message.contains(pattern, ignoreCase = true)) {
                return str(messageRes, UserMessages.GENERIC_ERROR)
            }
        }

        // Return generic message based on operation type
        return when {
            operationType.contains("login", ignoreCase = true) -> UserMessages.LOGIN_FAILED
            operationType.contains("register", ignoreCase = true) -> UserMessages.REGISTRATION_FAILED
            operationType.contains("password", ignoreCase = true) -> UserMessages.PASSWORD_CHANGE_FAILED
            operationType.contains("save", ignoreCase = true) -> UserMessages.SAVE_FAILED
            operationType.contains("add", ignoreCase = true) -> UserMessages.SAVE_FAILED
            operationType.contains("create", ignoreCase = true) -> UserMessages.SAVE_FAILED
            operationType.contains("update", ignoreCase = true) -> UserMessages.SAVE_FAILED
            operationType.contains("load", ignoreCase = true) -> UserMessages.LOAD_FAILED
            operationType.contains("fetch", ignoreCase = true) -> UserMessages.LOAD_FAILED
            operationType.contains("get", ignoreCase = true) -> UserMessages.LOAD_FAILED
            operationType.contains("delete", ignoreCase = true) -> UserMessages.DELETE_FAILED
            operationType.contains("remove", ignoreCase = true) -> UserMessages.DELETE_FAILED
            operationType.contains("upload", ignoreCase = true) -> UserMessages.UPLOAD_FAILED
            else -> UserMessages.GENERIC_ERROR
        }
    }
}
