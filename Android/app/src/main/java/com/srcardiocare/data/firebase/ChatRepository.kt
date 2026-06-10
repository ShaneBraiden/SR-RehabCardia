// ChatRepository.kt — Patient/clinician chat threads.
package com.srcardiocare.data.firebase

import com.srcardiocare.data.model.ChatMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Sends and observes chat messages stored under chats/{patientId}/messages. */
object ChatRepository {

    suspend fun sendChatMessage(patientId: String, senderId: String, senderName: String, text: String) {
        val msg = mapOf(
            "id" to UUID.randomUUID().toString(),
            "senderId" to senderId,
            "senderName" to senderName,
            "text" to text,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        FirebaseClients.db.collection("chats").document(patientId).collection("messages").add(msg).await()

        val receiverId = if (senderId == patientId) {
            val patientUser = runCatching { UserRepository.fetchUser(patientId) }.getOrNull().orEmpty()
            (patientUser["assignedDoctorId"] as? String)?.takeIf { it.isNotBlank() }
        } else {
            patientId
        }

        if (!receiverId.isNullOrBlank() && receiverId != senderId) {
            val senderRole = runCatching {
                (UserRepository.fetchUser(senderId)["role"] as? String ?: "").lowercase()
            }.getOrDefault("")

            com.srcardiocare.core.push.Notifier.send(
                com.srcardiocare.core.push.NotificationEvent.ChatMessage(
                    recipientId = receiverId,
                    senderName = senderName,
                    preview = text.take(90),
                    chatThreadId = patientId,
                    senderIsClinician = senderRole == "doctor" || senderRole == "admin"
                )
            )
        }
    }

    fun observeChatMessages(patientId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any?>>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = FirebaseClients.db.collection("chats").document(patientId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val msgs = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    data.toMutableMap().apply { put("docId", doc.id) }
                } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { listener.remove() }
    }

    /** Typed observe. Prefer this in new code. */
    fun observeChatMessagesTyped(patientId: String): kotlinx.coroutines.flow.Flow<List<ChatMessage>> =
        observeChatMessages(patientId).map { list -> list.map { it.toChatMessage(it["docId"] as? String ?: "") } }
}
