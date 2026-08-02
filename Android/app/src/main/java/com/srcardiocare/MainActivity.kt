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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
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
                // Asked once, before anything else is rendered: this is the one
                // screen whose own language cannot be assumed.
                var languageChosen by remember {
                    mutableStateOf(LocaleManager.hasChosenLanguage(this@MainActivity))
                }
                if (!languageChosen) {
                    LanguagePickerScreen(onChoose = { tag ->
                        LocaleManager.setLanguage(this@MainActivity, tag)
                        languageChosen = true
                        // Resources are bound in attachBaseContext, so the new
                        // locale only takes hold on a fresh activity.
                        recreate()
                    })
                    return@SRCardiocareTheme
                }

                // Wraps everything, including login. A build pulled because it
                // mishandles clinical data must not reach a dashboard, and the
                // sign-in screen is app content like any other. While a forced
                // update is showing, the block below never composes — auth is
                // not resolved and no Firestore read is issued.
                AppUpdateGate {
                    var startDest by remember { mutableStateOf<String?>(null) }

                    // Request POST_NOTIFICATIONS permission on Android 13+
                    val notifPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { /* granted or denied — notifications are optional */ }

                    LaunchedEffect(Unit) {
                        val auth = (application as SRCardiocareApp).awaitAuth()

                        startDest = when {
                            !auth.isLoggedIn -> Route.Login.path
                            auth.userRole == "ADMIN" -> Route.AdminDashboard.path
                            auth.userRole == "DOCTOR" -> Route.DoctorDashboard.path
                            else -> Route.PatientHome.path
                        }

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

                    if (startDest == null) {
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
                            onDecline = {
                                FirebaseService.logout()
                                // Recreating re-runs the auth resolution above,
                                // which lands on Login. Navigating instead would
                                // leave the authenticated back stack intact.
                                recreate()
                            }
                        ) {
                            val navController = rememberNavController()
                            Box(modifier = Modifier.fillMaxSize()) {
                                SRCardiocareNavGraph(
                                    navController = navController,
                                    startDestination = startDest!!
                                )
                            }
                        }
                    }
                }
            }
        }
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
