package com.srcardiocare

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.srcardiocare.core.auth.SessionTeardown
import com.srcardiocare.core.auth.signOutAndRestart
import com.srcardiocare.core.locale.LocaleManager
import com.srcardiocare.core.prefs.AppPreferences
import com.srcardiocare.ui.screens.onboarding.LanguagePickerScreen
import com.srcardiocare.core.push.PendingRoute
import com.srcardiocare.core.push.PushMessagingService
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.navigation.SRCardiocareNavGraph
import com.srcardiocare.navigation.Route
import com.srcardiocare.ui.components.AppUpdateGate
import com.srcardiocare.ui.components.ConsentGate
import com.srcardiocare.ui.theme.SRCardiocareTheme

class MainActivity : ComponentActivity() {

    /** Applies the patient's chosen language before any resource is resolved. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A shell starting is the signal that any sign-out is behind us — the
        // teardown deliberately leaves its blocker raised until something new
        // comes up, so that the outgoing screen never gets a frame back.
        SessionTeardown.onShellCreated()

        // Cold/warm launch from a push tap: capture the route before Compose starts.
        capturePushIntent(intent)

        setContent {
            // Theme is observed rather than read once, so the Settings screen
            // repaints the app the moment it changes — unlike language, which
            // has to recreate the activity to re-resolve resources. The direct
            // read comes first because it hydrates the flow from disk, so the
            // very first frame is already correct rather than a flash of the
            // default.
            val storedTheme = remember { AppPreferences.getThemeMode(this) }
            val liveTheme by AppPreferences.themeMode.collectAsState()
            val darkTheme = when (liveTheme ?: storedTheme) {
                AppPreferences.ThemeMode.LIGHT -> false
                AppPreferences.ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }

            SRCardiocareTheme(darkTheme = darkTheme) {
                // Auth resolution is hoisted above every gate on purpose.
                //
                // It used to live inside AppUpdateGate's content lambda, which
                // meant an update-status flip — re-checked on *every* resume —
                // moved the whole authenticated subtree to a different call
                // site, disposing it. That threw away the session, re-ran the
                // Firestore auth read, and rebuilt the consent gate from
                // scratch; a repaint the user only asked for by changing the
                // theme could take the same path. State that outlives a gate
                // must not be owned by the gate.
                var session by remember { mutableStateOf<Session?>(null) }

                // Request POST_NOTIFICATIONS permission on Android 13+
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* granted or denied — notifications are optional */ }

                LaunchedEffect(Unit) {
                    val auth = (application as SRCardiocareApp).awaitAuth()
                    val role = auth.userRole.orEmpty()

                    // A clinician must never inherit a patient's language.
                    // Signing out resets it, but a session that ended because
                    // the app was killed — or one carried over from a build
                    // older than that reset — can still leave Tamil applied to
                    // screens that were never translated. Correct it before the
                    // first frame. The recreate re-enters attachBaseContext with
                    // English, so this branch cannot run twice.
                    if (auth.isLoggedIn && role != "PATIENT" &&
                        LocaleManager.getLanguage(this@MainActivity) != LocaleManager.ENGLISH
                    ) {
                        LocaleManager.reset(this@MainActivity)
                        recreate()
                        return@LaunchedEffect
                    }

                    // A patient who has answered the language prompt on this
                    // device before is not asked again. Sign-out clears the
                    // handset-wide locale so the next person to hold the phone
                    // is not handed someone else's language — but the choice
                    // itself is kept per account, and this puts it back before
                    // the gate below gets the chance to prompt. Without it,
                    // every login looked to the gate like a first launch.
                    //
                    // Returns true only when the locale actually moved, so the
                    // recreate happens at most once and the second pass falls
                    // straight through to the session below.
                    val uid = FirebaseService.currentUID
                    if (auth.isLoggedIn && role == "PATIENT" && uid != null &&
                        LocaleManager.restoreFor(this@MainActivity, uid)
                    ) {
                        recreate()
                        return@LaunchedEffect
                    }

                    session = Session(
                        role = role,
                        startDestination = when {
                            !auth.isLoggedIn -> Route.Login.path
                            role == "ADMIN" -> Route.AdminDashboard.path
                            role == "DOCTOR" -> Route.DoctorDashboard.path
                            else -> Route.PatientHome.path
                        },
                        isLoggedIn = auth.isLoggedIn
                    )

                    // Refresh FCM token on every startup so Firestore always has a valid token.
                    FirebaseService.currentUID?.let { PushMessagingService.saveFcmToken(it) }

                    // Request notification permission after auth resolves (non-blocking)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Signing out reaches the network — it has to detach this
                // handset from the outgoing user's push fan-out while their
                // session is still valid — so it is not instant. Swap the whole
                // tree for a blocker while it runs: it tells the user the tap
                // registered, and replacing the authenticated subtree disposes
                // every Firestore listener in it before the teardown proceeds.
                val signingOut by SessionTeardown.inProgress.collectAsState()
                if (signingOut) {
                    SigningOutScreen()
                    return@SRCardiocareTheme
                }

                // Wraps everything, including login. A build pulled because it
                // mishandles clinical data must not reach a dashboard, and the
                // sign-in screen is app content like any other.
                AppUpdateGate {
                    val current = session
                    if (current == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                    } else {
                        // Nothing clinical is reachable until the signed-in user
                        // has accepted the current disclaimer and health-data
                        // disclosure. Placed here rather than inside the nav
                        // graph so no deep link from a push notification can
                        // route around it.
                        ConsentGate(
                            uid = FirebaseService.currentUID,
                            onDecline = { signOutAndRestart(this@MainActivity) }
                        ) {
                            // Language is a patient-facing choice: doctor and
                            // admin screens are not localised, so asking a
                            // clinician would offer them a half-translated app.
                            // Gated on a resolved session so it lands *after*
                            // first login rather than in front of it.
                            LanguageGate(
                                enabled = current.isLoggedIn && current.role == "PATIENT"
                            ) {
                                val navController = rememberNavController()
                                Box(modifier = Modifier.fillMaxSize()) {
                                    SRCardiocareNavGraph(
                                        navController = navController,
                                        startDestination = current.startDestination
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Shown while [SessionTeardown] runs. Intentionally has no controls: there
     * is nothing to cancel — the session is already being dismantled — and the
     * app relaunches itself the moment it finishes.
     */
    @Composable
    private fun SigningOutScreen() {
        // Reaching composition means the authenticated subtree has been
        // disposed, taking its Firestore snapshot listeners with it. The
        // teardown waits for this before it terminates the client to clear the
        // local cache — see SessionTeardown.blockerShown.
        LaunchedEffect(Unit) { SessionTeardown.onBlockerShown() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.sign_out_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    /** Everything the shell needs from auth, resolved once per activity. */
    private data class Session(
        val role: String,
        val startDestination: String,
        val isLoggedIn: Boolean
    )

    /**
     * Shows the one-time language choice when [enabled], then gets out of the way.
     *
     * [enabled] false is a pass-through rather than a "chosen" marker: a doctor
     * signing out of a shared handset must not consume the prompt that the
     * patient signing in after them is owed.
     */
    @Composable
    private fun LanguageGate(enabled: Boolean, content: @Composable () -> Unit) {
        var chosen by remember {
            mutableStateOf(LocaleManager.hasChosenLanguage(this@MainActivity))
        }

        if (!enabled || chosen) {
            content()
            return
        }

        LanguagePickerScreen(onChoose = { tag ->
            // Recorded against the uid as well as the handset, so this answer
            // survives the sign-out that clears the device-wide locale.
            LocaleManager.setLanguage(this@MainActivity, tag, FirebaseService.currentUID)
            chosen = true
            // Resources are bound in attachBaseContext, so the new locale only
            // takes hold on a fresh activity.
            recreate()
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        capturePushIntent(intent)
    }

    private fun capturePushIntent(intent: Intent?) {
        val route = intent?.getStringExtra(PushMessagingService.EXTRA_ROUTE).orEmpty()
        if (route.isBlank()) return
        val paramsJson = intent?.getStringExtra(PushMessagingService.EXTRA_PARAMS).orEmpty()
        val params = PushMessagingService.parseParams(paramsJson)
        PendingRoute.queue(route, params)
    }
}
