// InputValidator.kt — Centralized input validation for all user inputs
package com.srcardiocare.core.security

import android.util.Patterns

/**
 * Centralized input validation utility for the SR Cardiocare app.
 * Provides validation for all user-input fields with consistent error messages.
 */
object InputValidator {

    /**
     * Maximum length constants for various field types.
     * These limits prevent excessive data storage and potential DoS attacks.
     */
    object MaxLength {
        const val NAME = 100
        const val PHONE = 20
        /**
         * Account-creation phone fields accept a bare national mobile number
         * only — no country code, no separators. The temporary password is
         * derived from this exact string, so any stray character silently
         * produces credentials the clinician cannot read back correctly.
         */
        const val MOBILE = 10
        const val EMAIL = 254
        const val TEXT_FIELD = 500
        const val NOTES = 2000
        const val CHAT_MESSAGE = 1000
        const val EXERCISE_NAME = 100
        const val CATEGORY = 50
        const val INSTRUCTIONS = 5000
        const val LICENSE_NUMBER = 50
        const val CLINIC_NAME = 200
        const val INJURY_TYPE = 100
        const val SPECIALITY = 100
    }

    // Validation patterns
    private val NAME_PATTERN = Regex("^[\\p{L}\\s'\\-]+$")
    private val PHONE_PATTERN = Regex("^[+]?[\\d\\s()\\-]{7,20}$")
    private val YOUTUBE_ID_PATTERN = Regex("^[a-zA-Z0-9_-]{11}$")

    // Age validation bounds
    const val MIN_AGE = 1
    const val MAX_AGE = 150

    // Duration validation (seconds)
    const val MAX_DURATION_SECONDS = 86400 // 24 hours

    /**
     * Result of validation containing success status and optional error message.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val sanitizedValue: String = "",
        val errorMessage: String? = null
    )

    /**
     * Validates a name field (person's name).
     * Allows letters (including Unicode), spaces, hyphens, and apostrophes.
     */
    fun validateName(input: String, fieldName: String = "Name"): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(false, "", "$fieldName is required")
        }
        if (trimmed.length > MaxLength.NAME) {
            return ValidationResult(false, "", "$fieldName must be less than ${MaxLength.NAME} characters")
        }
        if (!NAME_PATTERN.matches(trimmed)) {
            return ValidationResult(false, "", "$fieldName contains invalid characters")
        }
        return ValidationResult(true, trimmed)
    }

    /**
     * Validates a phone number field.
     * Allows digits, spaces, dashes, parentheses, and optional leading plus.
     */
    fun validatePhone(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(true, "") // Phone is optional
        }
        if (!PHONE_PATTERN.matches(trimmed)) {
            return ValidationResult(false, "", "Please enter a valid phone number")
        }
        return ValidationResult(true, trimmed)
    }

    /**
     * Validates the mobile number used when creating a patient or doctor
     * account. Stricter than [validatePhone] on purpose: the temporary password
     * is `<number>@srcardio`, so the stored number and the number the clinician
     * reads off the screen have to be byte-identical. A country code, space or
     * dash typed here produces a password nobody can guess.
     *
     * Required, exactly [MaxLength.MOBILE] digits, digits only.
     */
    fun validateMobileNumber(input: String): ValidationResult {
        val digitsOnly = normalizeMobile(input)
        if (digitsOnly.isBlank()) {
            return ValidationResult(false, "", "Mobile number is required")
        }
        if (digitsOnly.length != MaxLength.MOBILE) {
            return ValidationResult(
                false,
                "",
                "Enter a ${MaxLength.MOBILE}-digit mobile number without country code"
            )
        }
        return ValidationResult(true, digitsOnly)
    }

    /**
     * Strips everything that is not a digit, then removes a leading country
     * code or trunk prefix.
     *
     * Written for two different inputs. Typing: the field simply stops at ten
     * digits, so an accidental eleventh keystroke is dropped rather than
     * shifting the number. Pasting: a contact copied as "+91 98765 43210" or
     * "09876543210" carries a prefix that has to come off the front, not the
     * back — trimming the tail would silently produce a different number.
     *
     * Prefix removal is deliberately conditional on the resulting length. A
     * ten-digit number that happens to start with 91 is a real number, not a
     * country code, and must survive untouched.
     */
    fun normalizeMobile(input: String): String {
        var digits = input.filter { it.isDigit() }

        // "+91 9876543210" -> "919876543210" -> "9876543210"
        if (digits.length == MaxLength.MOBILE + 2 && digits.startsWith("91")) {
            digits = digits.substring(2)
        }
        // "09876543210" -> "9876543210"
        if (digits.length == MaxLength.MOBILE + 1 && digits.startsWith("0")) {
            digits = digits.substring(1)
        }

        return if (digits.length > MaxLength.MOBILE) {
            digits.take(MaxLength.MOBILE)
        } else {
            digits
        }
    }

    /**
     * Validates an email address using Android's built-in pattern.
     */
    fun validateEmail(input: String): ValidationResult {
        val trimmed = input.trim().lowercase()
        if (trimmed.isBlank()) {
            return ValidationResult(false, "", "Email is required")
        }
        if (trimmed.length > MaxLength.EMAIL) {
            return ValidationResult(false, "", "Email is too long")
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            return ValidationResult(false, "", "Please enter a valid email address")
        }
        return ValidationResult(true, trimmed)
    }

    /**
     * Validates an age field.
     * Must be an integer between MIN_AGE and MAX_AGE.
     */
    fun validateAge(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(true, "") // Age may be optional
        }
        val age = trimmed.toIntOrNull()
        if (age == null || age < MIN_AGE || age > MAX_AGE) {
            return ValidationResult(false, "", "Age must be between $MIN_AGE and $MAX_AGE")
        }
        return ValidationResult(true, trimmed)
    }

    /**
     * Validates a duration field (in seconds).
     * Must be a positive integer up to MAX_DURATION_SECONDS.
     */
    fun validateDuration(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(true, "")
        }
        val duration = trimmed.toIntOrNull()
        if (duration == null || duration < 1 || duration > MAX_DURATION_SECONDS) {
            return ValidationResult(false, "", "Duration must be a positive number (max 24 hours)")
        }
        return ValidationResult(true, trimmed)
    }

    /**
     * Validates a generic text field with configurable max length.
     * Trims whitespace and enforces length limit.
     */
    fun validateTextField(
        input: String,
        maxLength: Int = MaxLength.TEXT_FIELD,
        fieldName: String = "Field",
        required: Boolean = false
    ): ValidationResult {
        val trimmed = input.trim()
        if (required && trimmed.isBlank()) {
            return ValidationResult(false, "", "$fieldName is required")
        }
        if (trimmed.length > maxLength) {
            return ValidationResult(
                false,
                trimmed.take(maxLength),
                "$fieldName exceeds maximum length of $maxLength characters"
            )
        }
        return ValidationResult(true, trimmed)
    }

    /**
     * Validates a YouTube video ID.
     * Must be exactly 11 characters: alphanumeric, dash, or underscore.
     */
    fun validateYouTubeVideoId(videoId: String): Boolean {
        return YOUTUBE_ID_PATTERN.matches(videoId)
    }

    /**
     * Validates hour input for time fields.
     * Must be an integer between 0 and 23.
     */
    fun validateHour(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(false, "", "Hour is required")
        }
        val hour = trimmed.toIntOrNull()
        if (hour == null || hour < 0 || hour > 23) {
            return ValidationResult(false, "", "Hour must be between 0 and 23")
        }
        return ValidationResult(true, trimmed)
    }

    /**
     * Validates minute input for time fields.
     * Must be an integer between 0 and 59.
     */
    fun validateMinute(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(false, "", "Minute is required")
        }
        val minute = trimmed.toIntOrNull()
        if (minute == null || minute < 0 || minute > 59) {
            return ValidationResult(false, "", "Minute must be between 0 and 59")
        }
        return ValidationResult(true, trimmed)
    }

    /**
     * Sanitizes text for safe display by removing control characters.
     */
    fun sanitizeForDisplay(input: String): String {
        return input.replace(Regex("[\\x00-\\x1F\\x7F]"), "").trim()
    }

    /**
     * Limits text length for input fields (use in onValueChange).
     * Returns the input truncated to maxLength if needed.
     */
    fun limitLength(input: String, maxLength: Int): String {
        return if (input.length > maxLength) input.take(maxLength) else input
    }
}
