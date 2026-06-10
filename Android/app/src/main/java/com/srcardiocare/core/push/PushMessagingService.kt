package com.srcardiocare.core.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.srcardiocare.MainActivity
import com.srcardiocare.R
import org.json.JSONObject

/**
 * Handles FCM traffic in every app state:
 *  - foreground: [onMessageReceived] fires → we post a heads-up notification ourselves
 *  - background / quit: data-only messages still route through [onMessageReceived]
 *    because we intentionally never send a `notification:` block from the server
 *
 * Keeping delivery data-only means the tap intent we build here is the one
 * that fires — FCM never generates a default system tap that bypasses our
 * deep-link routing.
 */
class PushMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return

        val title = data["title"].orEmpty().ifBlank { "SR-Cardiocare" }
        val body = data["body"].orEmpty()
        val route = data["route"].orEmpty()
        val channelId = data["channelId"]?.takeIf { it.isNotBlank() } ?: PushChannels.GENERAL
        val notificationId = data["notificationId"].orEmpty()
        val paramsJson = data["params"].orEmpty()
        val params = parseParams(paramsJson)

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
            putExtra(EXTRA_PARAMS, paramsJson)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId.hashCode().takeIf { it != 0 } ?: System.currentTimeMillis().toInt(), notification)

        // Consume the same params map inline so the sentinel stays with the tap.
        @Suppress("UNUSED_VARIABLE")
        val _params = params
    }

    override fun onNewToken(token: String) {
        val uid = com.srcardiocare.data.firebase.FirebaseService.currentUID ?: return
        saveFcmToken(uid, token)
    }

    companion object {
        private const val TAG = "PushMessagingService"
        const val EXTRA_ROUTE = "srcc.push.route"
        const val EXTRA_PARAMS = "srcc.push.params"
        const val EXTRA_NOTIFICATION_ID = "srcc.push.id"

        /**
         * Persists the current FCM registration token against the signed-in user so
         * the Cloud Function fan-out can target every device the user is signed into.
         * Safe to call on every login — Firestore `arrayUnion` dedupes.
         */
        fun saveFcmToken(uid: String, token: String? = null) {
            if (token != null) {
                write(uid, token)
                return
            }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { fetched -> write(uid, fetched) }
                .addOnFailureListener { Log.w(TAG, "FCM token fetch failed", it) }
        }

        private fun write(uid: String, token: String) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmTokens", FieldValue.arrayUnion(token))
                .addOnFailureListener { err ->
                    // Doc may not have the field yet — fall back to a set/merge.
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .set(mapOf("fcmTokens" to listOf(token)), com.google.firebase.firestore.SetOptions.merge())
                        .addOnFailureListener { Log.w(TAG, "fcmTokens merge failed", err) }
                }
        }

        fun parseParams(raw: String): Map<String, String> {
            if (raw.isBlank()) return emptyMap()
            return runCatching {
                val obj = JSONObject(raw)
                buildMap {
                    obj.keys().forEach { k -> put(k, obj.optString(k, "")) }
                }
            }.getOrDefault(emptyMap())
        }
    }
}
