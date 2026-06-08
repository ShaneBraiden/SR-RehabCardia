// ChatMappers.kt — Firestore document -> ChatMessage mapping.
package com.srcardiocare.data.firebase

import com.google.firebase.Timestamp
import com.srcardiocare.data.model.ChatMessage

/** Builds a [ChatMessage] from a chat message document (id + raw field map). */
fun Map<String, Any?>.toChatMessage(id: String): ChatMessage = ChatMessage(
    id = id,
    senderId = this["senderId"] as? String ?: "",
    senderName = this["senderName"] as? String ?: "",
    text = this["text"] as? String ?: "",
    timestampMs = (this["timestamp"] as? Timestamp)?.toDate()?.time
)
