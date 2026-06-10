package com.srcardiocare

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.srcardiocare.core.auth.AuthManager
import com.srcardiocare.core.push.PushChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * SR-Cardiocare Application class.
 *
 * Firebase and AuthManager are initialised on a background thread so the main
 * thread is never blocked during startup.  MainActivity suspends via [awaitAuth]
 * inside a LaunchedEffect — the first Compose frame renders immediately.
 */
class SRCardiocareApp : Application() {

    /** App-wide scope that survives configuration changes. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Deferred AuthManager — resolved on a background thread.
     * Callers should use [awaitAuth] rather than touching this directly.
     */
    private val authDeferred = appScope.async(Dispatchers.IO) {
        // FirebaseApp.initializeApp performs disk I/O (reads google-services.json).
        // Keeping it off the main thread removes the biggest startup block.
        FirebaseApp.initializeApp(this@SRCardiocareApp)
        
        // Initialize App Check after Firebase is ready
        initializeAppCheck()
        
        AuthManager(this@SRCardiocareApp)
    }

    /**
     * Initializes Firebase App Check with the appropriate provider.
     * - Debug builds: Uses DebugAppCheckProviderFactory (prints debug token to logcat)
     * - Release builds: Uses PlayIntegrityAppCheckProviderFactory (production attestation)
     */
    private fun initializeAppCheck() {
        val appCheck = FirebaseAppCheck.getInstance()
        
        if (BuildConfig.DEBUG) {
            // Debug provider — prints a debug token to logcat that you register
            // in Firebase Console → App Check → Apps → Manage debug tokens
            Log.d(TAG, "Initializing App Check with Debug provider")
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            // Production provider — uses Play Integrity API for attestation
            Log.d(TAG, "Initializing App Check with Play Integrity provider")
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }

    /**
     * Suspends until [AuthManager] is ready.  Safe to call from any coroutine;
     * subsequent calls return the already-resolved instance instantly.
     */
    suspend fun awaitAuth(): AuthManager = authDeferred.await()

    companion object {
        private const val TAG = "SRCardiocareApp"
        
        lateinit var instance: SRCardiocareApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Register notification channels for push + deep-link routing.
        PushChannels.register(this)

        // Kick off async init immediately — do NOT block here.
        // authDeferred is already started by the field initialiser above.
    }
}
