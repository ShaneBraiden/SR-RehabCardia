package com.srcardiocare.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

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
            NotificationChannel(GENERAL, "General alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Plan updates, feedback and general reminders"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHAT, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Direct messages between you and your care team"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(APPOINTMENTS, "Appointments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Appointment requests, confirmations and changes"
            }
        )
    }
}
