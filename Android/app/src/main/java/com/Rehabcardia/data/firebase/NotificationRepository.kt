// NotificationRepository.kt — In-app notification documents and live updates.
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.FieldValue
import com.srcardiocare.data.model.AppNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * Notification storage (collection: "notifications").
 *
 * Schema:
 *   id: string, userId: string (recipient), title: string, body: string,
 *   type: string (category for UI coloring), route: string (deep-link target),
 *   params: map<string,string> (route arguments), isRead: bool,
 *   createdAt: Timestamp (server)
 *
 * Writes are driven by `core.push.Notifier` — do not call [writeNotification]
 * directly from UI; emit a typed event through Notifier so every push has a
 * stable route + params contract.
 */
object NotificationRepository {

    /** Persists a notification document. Triggers the Cloud Function FCM fan-out. */
    suspend fun writeNotification(
        userId: String,
        title: String,
        body: String,
        type: String,
        route: String,
        params: Map<String, String> = emptyMap()
    ): String {
        val ref = FirebaseClients.db.collection("notifications").document()
        val data = mapOf(
            "id" to ref.id,
            "userId" to userId,
            "title" to title,
            "body" to body,
            "type" to type,
            "route" to route,
            "params" to params,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        ref.set(data).await()
        return ref.id
    }

    suspend fun fetchNotifications(userId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("notifications")
            .whereEqualTo("userId", userId)
            .limit(100)
            .get().await()
        return snapshot.documents
            .map { it.id to (it.data ?: emptyMap()) }
            .sortedByDescending { notificationEpochMillis(it.second["createdAt"]) }
            .take(50)
    }

    fun observeNotifications(userId: String): Flow<List<Pair<String, Map<String, Any?>>>> = callbackFlow {
        val registration = FirebaseClients.db.collection("notifications")
            .whereEqualTo("userId", userId)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val notifications = snapshot?.documents
                    ?.map { it.id to (it.data ?: emptyMap()) }
                    ?.sortedByDescending { notificationEpochMillis(it.second["createdAt"]) }
                    ?: emptyList()
                trySend(notifications)
            }
        awaitClose { registration.remove() }
    }

    suspend fun markNotificationRead(id: String) {
        FirebaseClients.db.collection("notifications").document(id)
            .update("isRead", true).await()
    }

    suspend fun markAllNotificationsRead(userId: String) {
        val snapshot = FirebaseClients.db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get().await()
        val batch = FirebaseClients.db.batch()
        for (doc in snapshot.documents) {
            batch.update(doc.reference, "isRead", true)
        }
        batch.commit().await()
    }

    suspend fun markNotificationsReadByType(userId: String, type: String) {
        val snapshot = FirebaseClients.db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("type", type)
            .whereEqualTo("isRead", false)
            .get().await()
        if (snapshot.isEmpty) return
        val batch = FirebaseClients.db.batch()
        for (doc in snapshot.documents) {
            batch.update(doc.reference, "isRead", true)
        }
        batch.commit().await()
    }

    private fun notificationEpochMillis(raw: Any?): Long = when (raw) {
        is com.google.firebase.Timestamp -> raw.toDate().time
        is String -> runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
        else -> 0L
    }

    // ── Typed reads ─────────────────────────────────────────────────────

    fun observeNotificationsTyped(userId: String): Flow<List<AppNotification>> =
        observeNotifications(userId).map { list -> list.map { (id, data) -> data.toNotification(id) } }

    suspend fun getNotificationsTyped(userId: String): List<AppNotification> =
        fetchNotifications(userId).map { (id, data) -> data.toNotification(id) }
}
