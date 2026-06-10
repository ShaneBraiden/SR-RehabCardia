package com.srcardiocare.core.push

import android.util.Log
import com.srcardiocare.data.firebase.FirebaseService

/**
 * Single entry-point for emitting push notifications.
 *
 * Call-sites pass a typed [NotificationEvent]; this object serialises it to a
 * Firestore document in `notifications/{id}`, which a Cloud Function then
 * fans out as an FCM data-only push to the recipient's device tokens.
 */
object Notifier {
    private const val TAG = "Notifier"

    suspend fun send(event: NotificationEvent) {
        if (event.userId.isBlank()) {
            Log.w(TAG, "Dropping ${event::class.simpleName}: blank recipient userId")
            return
        }
        runCatching {
            FirebaseService.writeNotification(
                userId = event.userId,
                title = event.title,
                body = event.body,
                type = event.type,
                route = event.route,
                params = event.params
            )
        }.onFailure { Log.e(TAG, "writeNotification failed for ${event::class.simpleName}", it) }
    }
}
