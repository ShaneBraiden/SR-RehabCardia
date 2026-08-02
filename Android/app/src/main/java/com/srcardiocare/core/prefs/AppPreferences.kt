// AppPreferences.kt — Device-local user preferences: appearance and notifications.
package com.srcardiocare.core.prefs

import android.content.Context
import com.srcardiocare.SRCardiocareApp
import com.srcardiocare.core.push.PushChannels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferences that belong to the device rather than the account.
 *
 * Deliberately not written to Firestore. A clinician using a ward tablet in a
 * bright room and their own phone at night wants different appearance settings
 * on each, and a notification the user muted on a shared device should not
 * follow them onto their personal one. These are also read on the startup path,
 * where a network round trip would be the wrong trade.
 *
 * Plain SharedPreferences for the same reason [com.srcardiocare.core.locale.LocaleManager]
 * uses them: none of this is secret, and an EncryptedSharedPreferences unlock
 * would cost real time during launch.
 */
object AppPreferences {

    /** Appearance. [SYSTEM] follows the device's own day/night setting. */
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    private const val PREFS = "rehabcardia_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_MUTED_CHANNELS = "muted_channels"
    private const val KEY_IN_APP_SOUND = "in_app_sound"

    /** Notification categories the user can silence, mapped to their channel. */
    val NOTIFICATION_CATEGORIES = listOf(
        PushChannels.GENERAL,
        PushChannels.CHAT,
        PushChannels.APPOINTMENTS
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Falls back to the Application context so non-UI callers can read too. */
    private fun anyContext(context: Context?): Context? =
        context ?: runCatching { SRCardiocareApp.instance }.getOrNull()

    // ── Theme ───────────────────────────────────────────────────────────

    private val _themeMode = MutableStateFlow<ThemeMode?>(null)

    /**
     * Observable so a change repaints immediately.
     *
     * Language has to recreate the activity — resources are resolved in
     * `attachBaseContext` — but colour is read during composition, so there is
     * no reason to make the user watch the screen blink for it.
     */
    val themeMode: StateFlow<ThemeMode?> = _themeMode.asStateFlow()

    fun getThemeMode(context: Context): ThemeMode {
        _themeMode.value?.let { return it }
        val stored = prefs(context).getString(KEY_THEME, null)
        val mode = ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
        _themeMode.value = mode
        return mode
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    // ── Notifications ───────────────────────────────────────────────────

    private val _mutedChannels = MutableStateFlow<Set<String>?>(null)
    val mutedChannels: StateFlow<Set<String>?> = _mutedChannels.asStateFlow()

    fun getMutedChannels(context: Context): Set<String> {
        _mutedChannels.value?.let { return it }
        val stored = prefs(context).getStringSet(KEY_MUTED_CHANNELS, emptySet()).orEmpty()
        _mutedChannels.value = stored
        return stored
    }

    fun setChannelMuted(context: Context, channelId: String, muted: Boolean) {
        val next = getMutedChannels(context).toMutableSet().apply {
            if (muted) add(channelId) else remove(channelId)
        }
        // A defensive copy: SharedPreferences does not copy the set it is
        // handed, and mutating a stored set in place is a documented way to
        // lose the write.
        prefs(context).edit().putStringSet(KEY_MUTED_CHANNELS, next.toSet()).apply()
        _mutedChannels.value = next
    }

    /**
     * Whether [channelId] should be shown at all. Called from the FCM service,
     * which has no Activity — hence the nullable context.
     *
     * This is a second gate in front of the system channel, not a replacement
     * for it: Android already lets the user mute a channel and choose its tone,
     * but only from system settings, which is several taps away and easy to
     * miss. Muting here is reversible from inside the app.
     */
    fun isChannelMuted(context: Context?, channelId: String): Boolean {
        val ctx = anyContext(context) ?: return false
        return channelId in getMutedChannels(ctx)
    }

    /** Whether in-app confirmations should make a sound. */
    fun isInAppSoundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IN_APP_SOUND, true)

    fun setInAppSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_IN_APP_SOUND, enabled).apply()
    }
}
