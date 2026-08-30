package com.srcardiocare.core.auth

import android.content.Context
import com.srcardiocare.core.locale.LocaleManager
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.findActivity

/**
 * The one way out of a session.
 *
 * Signing out is three things, and doing only the first two is what let a
 * clinician inherit a patient's Tamil UI on a shared handset:
 *
 *  1. drop the Firebase session and the cached role,
 *  2. reset the language to English and re-arm the first-run picker
 *     ([LocaleManager.reset]) — the choice belonged to the person leaving,
 *  3. recreate the activity, because resources are bound in `attachBaseContext`
 *     and a locale change cannot reach an activity that is already running.
 *
 * The recreate replaces the `navigate(Login) { popUpTo(0) }` the profile screens
 * used to do. Both land on the login screen — MainActivity re-resolves auth and
 * finds no session — but only the recreate re-reads the locale, and it drops the
 * authenticated back stack and every ViewModel with it rather than trusting a
 * pop to have cleared them.
 */
fun signOutAndRestart(context: Context) {
    FirebaseService.logout()
    AuthManager(context).clearAll()
    LocaleManager.reset(context)
    context.findActivity()?.recreate()
}
