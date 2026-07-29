package com.srcardiocare

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.srcardiocare.core.auth.AuthManager
import com.srcardiocare.core.locale.LocaleManager
import com.srcardiocare.core.push.PushChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
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

    /**
     * Applies the chosen language app-wide, so contexts that never go through an
     * Activity — notification building in particular — resolve localised strings.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    /** App-wide scope that survives configuration changes. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Deferred AuthManager — resolved on a background thread.
     * Callers should use [awaitAuth] rather than touching this directly.
     *
     * Deliberately `by lazy` and not a plain field initialiser: field initialisers
     * run in the Application *constructor*, which Android calls before
     * [attachBaseContext]. A coroutine started there races the base context being
     * attached, and Firebase — which reads resources — hits a null base and throws.
     * [onCreate] touches this to start the work as early as it is actually safe to.
     */
    private val authDeferred: Deferred<AuthManager> by lazy {
        appScope.async(Dispatchers.IO) {
            // FirebaseApp.initializeApp performs disk I/O (reads google-services.json).
            // Keeping it off the main thread removes the biggest startup block.
            FirebaseApp.initializeApp(this@SRCardiocareApp)

            // Initialize App Check after Firebase is ready
            initializeAppCheck()

            AuthManager(this@SRCardiocareApp)
        }
    }

    /**
     * Initializes Firebase App Check with the variant-specific provider.
     * The implementation lives in per-variant source sets (src/debug installs
     * the debug provider, src/release installs Play Integrity) so the
     * debug-only dependency is never referenced in release compilation.
     */
    private fun initializeAppCheck() {
        Log.d(TAG, "Initializing App Check")
        AppCheckProviderInstaller.install()
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

        // Kick off async init immediately — do NOT block here. Touching the lazy
        // starts the coroutine; the base context is attached by this point, so
        // Firebase can safely read resources off the main thread.
        authDeferred.start()
    }
}
