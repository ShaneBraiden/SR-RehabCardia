package com.srcardiocare.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.srcardiocare.R

/**
 * Registers the app's notification channels (Android 8+).
 *
 * Three channels let the user mute categories independently in system
 * settings without losing critical alerts:
 *  - [GENERAL]        — exercise assignments, feedback, general updates
 *  - [CHAT]           — direct messages between patient and clinician
 *  - [APPOINTMENTS]   — schedule changes, requests, confirmations
 */
object PushChannels {
    const val GENERAL = "srcc_general"
    const val CHAT = "srcc_chat"
    const val APPOINTMENTS = "srcc_appointments"

    fun register(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                GENERAL,
                context.getString(R.string.channel_general_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_general_desc)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHAT,
                context.getString(R.string.channel_chat_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_chat_desc)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                APPOINTMENTS,
                context.getString(R.string.channel_appointments_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_appointments_desc)
            }
        )
    }
}
