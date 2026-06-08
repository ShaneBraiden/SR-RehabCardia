// SecurePreferences.kt — Encrypted SharedPreferences wrapper
package com.srcardiocare.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Provides encrypted SharedPreferences using AndroidX Security library.
 * Uses AES256_GCM encryption for secure storage of sensitive data.
 */
object SecurePreferences {

    private const val TAG = "SecurePreferences"
    private const val ENCRYPTED_PREFS_NAME = "sr_cardiocare_secure_prefs"
    private const val LEGACY_PREFS_NAME = "sr_cardiocare_prefs"

    @Volatile
    private var instance: SharedPreferences? = null

    /**
     * Gets or creates the encrypted SharedPreferences instance.
     * Thread-safe with double-checked locking.
     *
     * @param context Application context
     * @return Encrypted SharedPreferences instance
     */
    fun getInstance(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: createEncryptedPreferences(context.applicationContext).also {
                instance = it
                // Migrate data from legacy unencrypted prefs if needed
                migrateLegacyPrefs(context.applicationContext, it)
            }
        }
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // SECURITY: Do NOT fall back to unencrypted preferences
            // This would expose sensitive user data. Instead, throw an exception
            // so the app fails fast and clearly indicates the security issue.
            Log.e(TAG, "CRITICAL: Failed to create encrypted preferences", e)
            throw SecurityException(
                "Unable to create secure storage. This device may not support " +
                "the required encryption. Please contact support.", e
            )
        }
    }

    /**
     * Migrates data from legacy unencrypted SharedPreferences to encrypted storage.
     * This is a one-time migration that runs automatically.
     */
    private fun migrateLegacyPrefs(context: Context, encryptedPrefs: SharedPreferences) {
        try {
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val legacyData = legacyPrefs.all

            if (legacyData.isNotEmpty()) {
                Log.i(TAG, "Migrating ${legacyData.size} entries from legacy preferences")

                val editor = encryptedPrefs.edit()
                for ((key, value) in legacyData) {
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                    }
                }
                editor.apply()

                // Clear legacy preferences after successful migration
                legacyPrefs.edit().clear().apply()
                Log.i(TAG, "Migration completed successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate legacy preferences", e)
        }
    }
}
