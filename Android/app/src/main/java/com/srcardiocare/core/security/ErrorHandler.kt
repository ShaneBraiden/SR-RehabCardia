// ErrorHandler.kt — Sanitizes error messages for user display
package com.srcardiocare.core.security

import android.util.Log
import com.google.firebase.auth.FirebaseAuthException

/**
 * Handles error message sanitization to prevent leaking implementation details.
 * Maps technical errors to user-friendly messages.
 */
object ErrorHandler {

    private const val TAG = "SRCardiocare"

    // Map of known Firebase/network error patterns to user-friendly messages
    private val errorMappings = mapOf(
        "INVALID_LOGIN_CREDENTIALS" to "Invalid email or password",
        "wrong-password" to "Invalid email or password",
        "user-not-found" to "No account found with this email",
        "email-already-in-use" to "An account with this email already exists",
        "weak-password" to "Password is too weak. Please use a stronger password",
        "network-request-failed" to "Network error. Please check your connection",
        "too-many-requests" to "Too many attempts. Please try again later",
        "user-disabled" to "This account has been disabled",
        "blocked by admin" to "Your account has been blocked by admin",
        "account access has been blocked" to "Your account has been blocked by admin",
        "invalid-email" to "Please enter a valid email address",
        "permission-denied" to "You don't have permission to perform this action",
        "unavailable" to "Service temporarily unavailable. Please try again",
        "PERMISSION_DENIED" to "You don't have permission to perform this action",
        "requires-recent-login" to "Please log out and log back in to continue",
        "credential-already-in-use" to "This credential is already associated with another account"
    )

    /**
     * Android FirebaseAuthException error codes -> user-friendly messages.
     * The Firebase Android SDK reports codes like "ERROR_WRONG_PASSWORD" (not the
     * kebab-case web codes), so these are matched first against [FirebaseAuthException.errorCode].
     */
    private val authCodeMappings = mapOf(
        "ERROR_INVALID_EMAIL" to "Please enter a valid email address",
        "ERROR_WRONG_PASSWORD" to "Incorrect password. Please try again",
        "ERROR_USER_NOT_FOUND" to "No account found with this email",
        "ERROR_INVALID_CREDENTIAL" to "Incorrect email or password",
        "ERROR_USER_DISABLED" to "This account has been disabled",
        "ERROR_USER_TOKEN_EXPIRED" to "Your session expired. Please log in again",
        "ERROR_TOO_MANY_REQUESTS" to "Too many attempts. Please try again later",
        "ERROR_NETWORK_REQUEST_FAILED" to "Network error. Please check your connection",
        "ERROR_EMAIL_ALREADY_IN_USE" to "An account with this email already exists",
        "ERROR_WEAK_PASSWORD" to "Password is too weak. Please use a stronger password",
        "ERROR_REQUIRES_RECENT_LOGIN" to "Please log out and log back in to continue"
    )

    /**
     * Generic user-friendly messages for different operation types.
     */
    object UserMessages {
        const val LOGIN_FAILED = "Login failed. Please try again"
        const val REGISTRATION_FAILED = "Registration failed. Please try again"
        const val SAVE_FAILED = "Failed to save. Please try again"
        const val LOAD_FAILED = "Failed to load data. Please try again"
        const val DELETE_FAILED = "Failed to delete. Please try again"
        const val UPLOAD_FAILED = "Upload failed. Please try again"
        const val NETWORK_ERROR = "Network error. Please check your connection"
        const val GENERIC_ERROR = "An error occurred. Please try again"
        const val PASSWORD_CHANGE_FAILED = "Failed to change password. Please try again"
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
            authCodeMappings[exception.errorCode]?.let { return it }
        }

        // Check for known error patterns
        for ((pattern, userMessage) in errorMappings) {
            if (message.contains(pattern, ignoreCase = true)) {
                return userMessage
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
