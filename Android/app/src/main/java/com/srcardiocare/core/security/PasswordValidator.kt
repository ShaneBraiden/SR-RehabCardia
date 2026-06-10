// PasswordValidator.kt — Strong password validation for the app
package com.srcardiocare.core.security

/**
 * Password validation utility enforcing strong password requirements.
 * Requires: 8+ characters, uppercase, lowercase, and digit.
 */
object PasswordValidator {

    private const val MIN_LENGTH = 8

    /**
     * Result of password validation.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Validates a password against security requirements.
     * Requirements:
     * - At least 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     */
    fun validate(password: String): ValidationResult {
        if (password.length < MIN_LENGTH) {
            return ValidationResult(false, "Password must be at least $MIN_LENGTH characters")
        }
        if (!password.any { it.isUpperCase() }) {
            return ValidationResult(false, "Password must contain at least one uppercase letter")
        }
        if (!password.any { it.isLowerCase() }) {
            return ValidationResult(false, "Password must contain at least one lowercase letter")
        }
        if (!password.any { it.isDigit() }) {
            return ValidationResult(false, "Password must contain at least one number")
        }
        return ValidationResult(true)
    }

    /**
     * Returns a human-readable string describing password requirements.
     */
    fun getRequirementsText(): String =
        "Password must be at least 8 characters with uppercase, lowercase, and a number"
}
