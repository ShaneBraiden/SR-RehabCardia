// ConsentManager.kt — Records that the user has read the medical disclaimer and
// agreed to the health-data disclosure.
package com.srcardiocare.core.consent

import android.content.Context

/**
 * Tracks acceptance of the pre-use disclosures required of a health app:
 * the medical disclaimer (what this app is and is not) and the prominent
 * disclosure of what health data is collected and who it reaches.
 *
 * Acceptance is stored twice, deliberately:
 *
 *  - **Locally**, keyed by uid and version, because the gate runs on the launch
 *    path. Making a Firestore round trip decide whether to show a blocking
 *    screen means a patient on a slow ward connection either stares at a
 *    spinner or — worse, if we failed open — reaches clinical content having
 *    consented to nothing.
 *  - **In Firestore** (`users/{uid}.consent`), because consent that only exists
 *    on the handset is not evidence. A device wipe or a reinstall re-prompts,
 *    which is the correct failure direction: asking twice is harmless, assuming
 *    consent that was never given is not.
 *
 * [CURRENT_VERSION] is the mechanism for re-consent. Bump it whenever the
 * wording changes in a way a reasonable patient would want to re-read, and
 * everyone is asked again on next launch.
 */
object ConsentManager {

    /** Version of the disclaimer + disclosure wording currently shipping. */
    const val CURRENT_VERSION = 1

    private const val PREFS = "rehabcardia_consent"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Per-account: a shared ward tablet must not carry one patient's consent
     *  over to the next person who signs in. */
    private fun key(uid: String) = "consent_v${CURRENT_VERSION}_$uid"

    fun hasAccepted(context: Context, uid: String): Boolean =
        prefs(context).getBoolean(key(uid), false)

    fun markAccepted(context: Context, uid: String) {
        prefs(context).edit().putBoolean(key(uid), true).apply()
    }

    /**
     * Drops the local record so the gate shows again. Used when the user
     * declines, so that a decline is never mistaken for silent acceptance.
     */
    fun clear(context: Context, uid: String) {
        prefs(context).edit().remove(key(uid)).apply()
    }
}
