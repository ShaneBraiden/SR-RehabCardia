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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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

        val title = data["title"].orEmpty().ifBlank { "RehabCardia" }
        val body = data["body"].orEmpty()
        val route = data["route"].orEmpty()
        val channelId = data["channelId"]?.takeIf { it.isNotBlank() } ?: PushChannels.GENERAL

        // Respect the in-app category switches. The message is still delivered
        // and still lands in the in-app notifications list — only the system
        // notification is suppressed, so muting cannot lose a clinical update.
        if (com.srcardiocare.core.prefs.AppPreferences.isChannelMuted(this, channelId)) return

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

        /** Upper bound on how long a token operation may delay signing out. */
        private const val TOKEN_OP_TIMEOUT_MS = 3_000L

        /**
         * Detaches this handset from [uid]'s push fan-out. Call while the user
         * is still signed in — the `users/{uid}` write needs their session.
         *
         * Two steps, and the second is the one that actually guarantees it:
         *
         *  1. `arrayRemove` the token from the outgoing user's document, so the
         *     fan-out stops naming this device. Best effort — a handset with no
         *     signal cannot do this, and sign-out must not be blocked on it.
         *  2. Delete the registration token outright. This is what makes the
         *     leak impossible rather than merely unlikely: whatever documents
         *     still list the old token, FCM now rejects it as `UNREGISTERED`
         *     and delivers nothing. The next sign-in mints a fresh token.
         *
         * Without this, `arrayUnion` left one device on every account that had
         * ever signed into it, so a patient's clinical push notifications kept
         * arriving on a handset the next person was already using.
         */
        suspend fun detachDevice(uid: String) {
            val messaging = FirebaseMessaging.getInstance()

            val token = runCatching {
                withTimeoutOrNull(TOKEN_OP_TIMEOUT_MS) { messaging.token.await() }
            }.getOrNull()

            if (token != null) {
                runCatching {
                    withTimeoutOrNull(TOKEN_OP_TIMEOUT_MS) {
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .update("fcmTokens", FieldValue.arrayRemove(token))
                            .await()
                    }
                }.onFailure { Log.w(TAG, "fcmTokens arrayRemove failed", it) }
            }

            runCatching {
                withTimeoutOrNull(TOKEN_OP_TIMEOUT_MS) { messaging.deleteToken().await() }
            }.onFailure { Log.w(TAG, "deleteToken failed", it) }
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
