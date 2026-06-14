// Debug variant: installs the App Check debug provider, which prints a debug
// token to logcat that you register in Firebase Console → App Check →
// Apps → Manage debug tokens.
package com.srcardiocare

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckProviderInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
