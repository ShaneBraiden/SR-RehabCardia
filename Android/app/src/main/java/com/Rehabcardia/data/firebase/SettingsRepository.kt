// SettingsRepository.kt — Global app settings and access-control policy.
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class AccessControlSettings(
    val sessionLocksEnabled: Boolean = true,
    val blockAllPatients: Boolean = false,
    val blockAllDoctors: Boolean = false
)

/** Reads and writes the global app settings document (settings/appSettings). */
object SettingsRepository {

    suspend fun fetchAccessControlSettings(): AccessControlSettings {
        return try {
            val doc = FirebaseClients.db.collection("settings").document("appSettings").get().await()
            if (!doc.exists()) return AccessControlSettings()

            AccessControlSettings(
                sessionLocksEnabled = doc.getBoolean("sessionLocksEnabled") ?: true,
                blockAllPatients = doc.getBoolean("blockAllPatients") ?: false,
                blockAllDoctors = doc.getBoolean("blockAllDoctors") ?: false
            )
        } catch (_: Exception) {
            AccessControlSettings()
        }
    }

    suspend fun updateAccessControlSettings(
        sessionLocksEnabled: Boolean? = null,
        blockAllPatients: Boolean? = null,
        blockAllDoctors: Boolean? = null
    ) {
        val updates = mutableMapOf<String, Any>()
        sessionLocksEnabled?.let { updates["sessionLocksEnabled"] = it }
        blockAllPatients?.let { updates["blockAllPatients"] = it }
        blockAllDoctors?.let { updates["blockAllDoctors"] = it }
        if (updates.isEmpty()) return

        FirebaseClients.db.collection("settings").document("appSettings")
            .set(updates, SetOptions.merge())
            .await()
    }

    suspend fun fetchSessionLocksEnabled(): Boolean {
        return fetchAccessControlSettings().sessionLocksEnabled
    }

    suspend fun updateSessionLocksEnabled(enabled: Boolean) {
        updateAccessControlSettings(sessionLocksEnabled = enabled)
    }
}
