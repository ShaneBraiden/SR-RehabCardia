package com.srcardiocare.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import com.srcardiocare.SRCardiocareApp
import java.util.Locale

/**
 * App language selection.
 *
 * English is the default; Tamil is opt-in and is offered to patients only —
 * doctor and admin screens are not localised.
 *
 * We deliberately do NOT use `AppCompatDelegate.setApplicationLocales()`. The app
 * is pure Compose: [com.srcardiocare.MainActivity] extends ComponentActivity, there
 * is no androidx.appcompat dependency, and the XML theme descends from
 * `android:Theme.Material.Light.NoActionBar`. Below API 33 the AndroidX backport
 * only applies itself to `AppCompatActivity`, so adopting it would force both an
 * activity-base and a theme-parent change for no benefit at minSdk 26.
 *
 * Instead the chosen tag is stored in prefs and applied by wrapping the base
 * context in `attachBaseContext` — one mechanism, identical on API 26 through 36.
 * Callers change the language with [setLanguage] and then recreate the activity.
 */
object LocaleManager {

    const val ENGLISH = "en"
    const val TAMIL = "ta"

    /** Language tags the app ships translations for, in the order they're offered. */
    val SUPPORTED = listOf(ENGLISH, TAMIL)

    private const val PREFS = "rehabcardia_locale"
    private const val KEY_LANGUAGE = "language_tag"
    private const val KEY_CHOSEN = "language_chosen"

    /**
     * Plain SharedPreferences, not [com.srcardiocare.core.security.SecurePreferences].
     * A locale tag is not a secret, and this is read on the `attachBaseContext`
     * startup path where an EncryptedSharedPreferences unlock would cost real time.
     */
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Currently selected tag, defaulting to [ENGLISH]. */
    fun getLanguage(context: Context): String {
        val stored = prefs(context).getString(KEY_LANGUAGE, null)
        return if (stored in SUPPORTED) stored!! else ENGLISH
    }

    /**
     * Persists [tag]. Does not take effect until the activity is recreated —
     * callers are expected to follow this with `activity.recreate()`.
     */
    fun setLanguage(context: Context, tag: String) {
        val safe = if (tag in SUPPORTED) tag else ENGLISH
        prefs(context).edit()
            .putString(KEY_LANGUAGE, safe)
            .putBoolean(KEY_CHOSEN, true)
            .apply()
    }

    /**
     * Whether the signed-in user has picked a language yet.
     *
     * Distinguishing "chose English" from "never asked" is the whole point:
     * the stored tag defaults to English either way, so without this flag the
     * first-run prompt could not tell a Tamil reader who has not been asked yet
     * from an English reader who has. The prompt is shown once, on first launch
     * after signing in, and not again until the next sign-out clears it via
     * [reset] — which is also why the login screen needs no toggle of its own.
     */
    fun hasChosenLanguage(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHOSEN, false)

    /**
     * Clears the language choice: back to English, and the first-run prompt
     * armed again for whoever signs in next.
     *
     * Called on every sign-out. The locale is a device-wide, process-wide
     * setting but the person it was chosen for is not — on a shared clinic
     * handset the next user is routinely a different patient, or a clinician
     * whose screens are English-only. Leaving Tamil applied would hand them an
     * app in a language they may not read, with no visible control to change it
     * (the switch lives behind a patient login). Resetting here is what makes
     * [hasChosenLanguage] mean "chosen by the person currently signed in".
     *
     * Takes effect on the next activity creation, exactly like [setLanguage] —
     * callers sign out and recreate.
     */
    fun reset(context: Context) {
        prefs(context).edit()
            .remove(KEY_LANGUAGE)
            .remove(KEY_CHOSEN)
            .apply()
    }

    fun isTamil(context: Context) = getLanguage(context) == TAMIL

    /**
     * Returns [base] re-configured for the stored language. Call from
     * `attachBaseContext` in both the Application and the Activity — the
     * Application override is what gives notifications and other non-activity
     * contexts the right resources.
     */
    /**
     * A locale-wrapped application context for non-UI classes (validators, the
     * error catalogue) that must resolve strings but are never handed one.
     *
     * Re-wraps on every call by design: the Application's own locale is fixed at
     * process start, so reusing it directly would keep serving the previous
     * language after a switch until the process restarts. Returns null if the
     * Application isn't up yet, so callers can fall back.
     */
    fun appContext(): Context? =
        runCatching { wrap(SRCardiocareApp.instance) }.getOrNull()

    /** Resolves [id] against [appContext], falling back to [fallback] if unavailable. */
    fun string(@StringRes id: Int, fallback: String): String =
        appContext()?.let { runCatching { it.getString(id) }.getOrNull() } ?: fallback

    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(getLanguage(base))
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))

        return base.createConfigurationContext(config)
    }
}
