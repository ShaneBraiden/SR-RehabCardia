package com.srcardiocare.core.auth

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.srcardiocare.core.consent.ConsentManager
import com.srcardiocare.core.locale.LocaleManager
import com.srcardiocare.core.push.PendingRoute
import com.srcardiocare.core.push.PushMessagingService
import com.srcardiocare.data.firebase.CurrentUserTours
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.SessionRepository
import com.srcardiocare.ui.components.findActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * The one way out of a session.
 *
 * This handset is shared. A ward tablet passes between patients; a clinician
 * signs in on it between rounds. So the bar for signing out is not "the next
 * screen is the login screen" — it is that the account which signs in next
 * inherits *nothing*: no cached documents, no push subscription, no queued deep
 * link, no language, no notifications sitting in the tray.
 *
 * Meeting that bar takes work in a specific order, because each step depends on
 * the one before it still being possible:
 *
 *  1. **While still authenticated** — detaching this device from the outgoing
 *     user's push fan-out is a write to *their* user document, so it cannot
 *     wait until after `signOut()`; the rules would reject it.
 *  2. **The session itself** — Firebase Auth and the encrypted role cache.
 *  3. **Every singleton holding something that belonged to the account**, plus
 *     the two things the next user would otherwise walk into: notifications
 *     already in the tray, and the previous user's language.
 *  4. **Firestore's on-disk cache** — last, because clearing it means
 *     terminating the client, which is only safe once the UI holding snapshot
 *     listeners is gone.
 *
 * [inProgress] is published so the shell can put a blocker screen up, and step
 * 4 waits for that screen to report in via [onBlockerShown] before it touches
 * Firestore. The screen is doing two jobs: swapping the tree is what disposes
 * the listeners, and it also means a sign-out that reaches the network does not
 * look like a button that did nothing.
 *
 * Every step is idempotent, and a `sign_out_pending` flag outlives the sequence
 * so a process killed part-way through is finished off on the next launch
 * instead of leaving an account half signed in.
 */
object SessionTeardown {

    private const val TAG = "SessionTeardown"

    /**
     * Plain SharedPreferences, and deliberately not [com.srcardiocare.core.security.SecurePreferences]:
     * this is read on the launch path before anything else runs, and it holds
     * one boolean that is not a secret. It is also not the encrypted store
     * *because* sign-out wipes that one — a flag saying "the sign-out did not
     * finish" cannot live inside what the sign-out erases.
     */
    private const val PREFS = "rehabcardia_session"

    /**
     * Set before the parts of the teardown that can fail, cleared once they
     * have not. A "this sign-out may be incomplete" marker rather than a to-do
     * list: everything it triggers is safe to run again.
     */
    private const val KEY_SIGN_OUT_PENDING = "sign_out_pending"

    /** Upper bound on the cache clear; a slow disk must not hang sign-out. */
    private const val CACHE_CLEAR_TIMEOUT_MS = 3_000L

    /**
     * Upper bound on waiting for the blocker screen. If the shell never reports
     * in, carry on: the cache clear is wrapped and its failure is recoverable,
     * whereas a sign-out that never finishes is not.
     */
    private const val BLOCKER_TIMEOUT_MS = 1_000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _inProgress = MutableStateFlow(false)

    /** True while the teardown runs, so the shell can block the UI behind it. */
    val inProgress: StateFlow<Boolean> = _inProgress.asStateFlow()

    /**
     * Completed by [onBlockerShown] once the blocker screen is composed.
     *
     * Terminating Firestore is only safe after the screens holding snapshot
     * listeners have left the composition, and "the blocker is on screen" is
     * precisely that fact — a composition that has been applied is one whose
     * old subtree has been disposed. Waiting on this rather than on a delay
     * keeps the ordering a guarantee instead of a guess: on the path where
     * nothing else in the teardown suspends (no session left to detach), a
     * timing assumption would not hold at all.
     */
    @Volatile
    private var blockerShown: CompletableDeferred<Unit>? = null

    /**
     * Outlives the Activity on purpose. The teardown finishes by recreating the
     * very screen that started it, so it cannot run in that screen's scope.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Runs the teardown. Idempotent: a second tap while the first is in flight
     * is ignored rather than racing it.
     */
    fun signOut(context: Context) {
        if (!_inProgress.compareAndSet(expect = false, update = true)) return

        val appContext = context.applicationContext
        val activity = context.findActivity()
        val blocker = CompletableDeferred<Unit>().also { blockerShown = it }

        scope.launch {
            // ── 1. Needs the session that is about to end ───────────────────
            FirebaseService.currentUID?.let { uid ->
                runCatching { PushMessagingService.detachDevice(uid) }
                    .onFailure { Log.w(TAG, "push detach failed", it) }
            }

            // ── 2. Drop the session ────────────────────────────────────────
            // logout() clears AuthRepository's cached role and doctor
            // assignment; clearAll() empties the encrypted preferences, which
            // is where the role the shell reads on launch is kept.
            FirebaseService.logout()
            AuthManager(appContext).clearAll()

            // ── 3. Singletons holding something that belonged to the account ─
            CurrentUserTours.clear()
            PendingRoute.clear()
            ConsentManager.clearSessionCache()
            SessionRepository.clearRateLimiters()

            // ── 4. What the next user would otherwise see or hear ───────────
            // Notifications already posted are the outgoing user's clinical
            // messages, and they stay in the tray — and tappable — across a
            // sign-out unless something takes them down.
            runCatching {
                val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as? NotificationManager
                manager?.cancelAll()
            }.onFailure { Log.w(TAG, "cancelAll failed", it) }

            // The locale belonged to the person leaving. A clinician signing in
            // after a Tamil-reading patient must not get half-translated screens.
            LocaleManager.reset(appContext)

            // ── 5. Firestore's on-disk cache ───────────────────────────────
            // Only once the blocker is up, because clearing the cache means
            // terminating the client and every screen holding a snapshot
            // listener has to be gone first.
            withTimeoutOrNull(BLOCKER_TIMEOUT_MS) { blocker.await() }

            // Armed first: if the process dies during the clear — the user
            // force-closing the app mid-sign-out — the next launch redoes it
            // before anything can read from the cache.
            setPending(appContext, true)
            if (clearFirestoreCache()) setPending(appContext, false)

            // ── 6. Back to a login screen with nothing behind it ────────────
            // [inProgress] stays true across this call and is cleared by the
            // shell that comes up — see [onShellCreated]. Clearing it here
            // instead would let the *outgoing* composition render one more
            // frame first, and it still holds the previous user's resolved
            // session, so the dashboard flashes back before the recreate lands.
            restartUi(appContext, activity)
        }
    }

    /**
     * Called by the shell as it starts, to take the blocker screen down.
     *
     * Only [signOut] raises the flag, and it raises it for exactly as long as
     * it takes to get a new shell on screen — so a shell being created is the
     * signal that the teardown is behind us.
     */
    fun onShellCreated() {
        _inProgress.value = false
    }

    /**
     * Called by the blocker screen once it has been composed, which is the
     * moment the authenticated subtree — and every snapshot listener in it —
     * has been disposed. See [blockerShown].
     */
    fun onBlockerShown() {
        blockerShown?.complete(Unit)
    }

    /**
     * Empties Firestore's local cache. Returns whether it actually happened.
     *
     * Firestore keeps an on-disk copy of every document it has read, and that
     * cache belongs to the install, not to the account. Left in place, a
     * handset with no signal serves the previous user's clinical records to
     * whoever signs in next — the security rules never get a say, because the
     * read never leaves the device.
     *
     * `clearPersistence()` refuses to run on a live client, so the instance has
     * to be terminated first. That is safe here and nowhere earlier: [signOut]
     * raised the blocker screen before this ran, which disposed every
     * composable holding a snapshot listener, and
     * [FirebaseClients][com.srcardiocare.data.firebase.FirebaseClients] resolves
     * `db` per call, so nothing is left holding the terminated instance.
     */
    private suspend fun clearFirestoreCache(): Boolean {
        val firestore = FirebaseFirestore.getInstance()
        return runCatching {
            withTimeoutOrNull(CACHE_CLEAR_TIMEOUT_MS) {
                firestore.terminate().await()
                firestore.clearPersistence().await()
                true
            }
        }.onFailure {
            Log.w(TAG, "in-process cache clear failed; deferring to next launch", it)
        }.getOrNull() ?: false
    }

    /**
     * Clears the cache on the launch path when the last sign-out could not.
     *
     * Called from `Application.onCreate`, which is the only other moment this
     * works: `clearPersistence()` fails on a client that has already been used,
     * and every Firestore read in the app happens after this point — including
     * the update gate, which does not wait for auth to resolve.
     *
     * It blocks the launch thread, which is the trade. It runs on one launch
     * per failed sign-out and deletes a local directory; the alternative is
     * racing the rest of the app for who touches Firestore first.
     */
    fun clearFirestoreCacheIfRequested(context: Context) {
        if (!prefs(context).getBoolean(KEY_SIGN_OUT_PENDING, false)) return

        runCatching {
            Tasks.await(
                FirebaseFirestore.getInstance().clearPersistence(),
                CACHE_CLEAR_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )
        }.onFailure { Log.w(TAG, "launch-path clearPersistence failed", it) }
    }

    /**
     * Finishes a sign-out that its own process did not live to complete.
     *
     * Signing out writes to two preference stores and asks Firebase Auth to
     * write to a third. If the app is force-closed between those writes and the
     * end of the teardown, some of them may not have landed. Rather than reason
     * about which, this repeats the session-dropping half on the way back up,
     * where nothing is racing it — `clearAll()` is idempotent, so the ordinary
     * case (nothing pending) costs a single boolean read.
     *
     * Runs on the background thread that builds [AuthManager] and before
     * anything reads the auth state, so the shell never sees a stale session.
     * The flag clears here because this is the last thing a sign-out owes.
     */
    fun finishPendingSignOut(context: Context, authManager: AuthManager) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_SIGN_OUT_PENDING, false)) return

        runCatching { authManager.clearAll() }
            .onFailure { Log.w(TAG, "deferred clearAll failed", it) }

        prefs.edit().remove(KEY_SIGN_OUT_PENDING).commit()
    }

    /**
     * commit() rather than apply() throughout: this flag exists to survive the
     * process dying, which is exactly what a queued write does not do.
     */
    private fun setPending(context: Context, pending: Boolean) {
        val editor = prefs(context).edit()
        if (pending) editor.putBoolean(KEY_SIGN_OUT_PENDING, true)
        else editor.remove(KEY_SIGN_OUT_PENDING)
        editor.commit()
    }

    /**
     * Brings the app back up on the login screen.
     *
     * `recreate()` rather than a navigation: it drops the authenticated back
     * stack and every ViewModel with it rather than trusting a pop to have
     * cleared them, and it is the only thing that re-enters `attachBaseContext`,
     * which is what makes the language reset above take hold.
     */
    private fun restartUi(appContext: Context, activity: Activity?) {
        if (activity != null) {
            activity.recreate()
            return
        }

        // No Activity behind the context we were handed. Start the task fresh.
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) {
            appContext.startActivity(intent)
        } else {
            // Nothing will come up to clear the flag, so clear it here rather
            // than leave whatever is on screen stuck behind the blocker.
            Log.w(TAG, "no activity and no launch intent; UI not restarted")
            _inProgress.value = false
        }
    }
}

/**
 * Signs the current user out and brings the app back up empty.
 *
 * Kept as a top-level function because every screen with a sign-out control
 * calls exactly this and nothing else — there is no second way out, and adding
 * one is how a device ends up half signed out. See [SessionTeardown].
 */
fun signOutAndRestart(context: Context) = SessionTeardown.signOut(context)
