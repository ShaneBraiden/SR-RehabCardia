// NotificationMappers.kt — Firestore document -> AppNotification mapping.
package com.srcardiocare.data.firebase

import com.google.firebase.Timestamp
import com.srcardiocare.data.model.AppNotification

/** Builds an [AppNotification] from a Firestore `notifications` document. */
fun Map<String, Any?>.toNotification(id: String): AppNotification = AppNotification(
    id = id,
    userId = this["userId"] as? String ?: "",
    title = this["title"] as? String ?: "",
    body = this["body"] as? String ?: "",
    type = this["type"] as? String ?: "",
    route = this["route"] as? String ?: "",
    params = (this["params"] as? Map<*, *>)?.mapNotNull { (k, v) ->
        val key = k as? String ?: return@mapNotNull null
        val value = v as? String ?: return@mapNotNull null
        key to value
    }?.toMap() ?: emptyMap(),
    isRead = this["isRead"] as? Boolean ?: false,
    createdAtMs = (this["createdAt"] as? Timestamp)?.toDate()?.time
)
