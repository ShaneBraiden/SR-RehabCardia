// PasswordGenerator.kt — Secure temporary password generation
package com.srcardiocare.core.security

import java.security.SecureRandom

/**
 * Generates secure temporary passwords for new user accounts.
 * Uses SecureRandom for cryptographically strong random generation.
 */
object PasswordGenerator {

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SPECIAL = "!@#$%&*"

    private val ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL

    /**
     * Generates a secure temporary password.
     * Guarantees at least one character from each required category.
     *
     * @param length Total password length (default: 12, minimum: 8)
     * @return A secure random password
     */
    fun generateTemporaryPassword(length: Int = 12): String {
        val actualLength = length.coerceAtLeast(8)
        val random = SecureRandom()
        val password = StringBuilder()

        // Ensure at least one of each required type
        password.append(UPPERCASE[random.nextInt(UPPERCASE.length)])
        password.append(LOWERCASE[random.nextInt(LOWERCASE.length)])
        password.append(DIGITS[random.nextInt(DIGITS.length)])
        password.append(SPECIAL[random.nextInt(SPECIAL.length)])

        // Fill remaining with random chars from all categories
        repeat(actualLength - 4) {
            password.append(ALL_CHARS[random.nextInt(ALL_CHARS.length)])
        }

        // Shuffle the password to avoid predictable patterns
        return password.toList().shuffled(random).joinToString("")
    }
}
