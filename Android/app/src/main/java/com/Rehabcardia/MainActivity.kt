package com.srcardiocare

import android.Manifest
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.srcardiocare.core.push.PendingRoute
import com.srcardiocare.core.push.PushMessagingService
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.navigation.SRCardiocareNavGraph
import com.srcardiocare.navigation.Route
import com.srcardiocare.ui.components.PoweredByBadge
import com.srcardiocare.ui.theme.DesignTokens
import com.srcardiocare.ui.theme.SRCardiocareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cold/warm launch from a push tap: capture the route before Compose starts.
        capturePushIntent(intent)

        setContent {
            SRCardiocareTheme {
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
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        PoweredByBadge(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(bottom = DesignTokens.Spacing.SM)
                        )
                    }
                } else {
                    val navController = rememberNavController()
                    Box(modifier = Modifier.fillMaxSize()) {
                        SRCardiocareNavGraph(
                            navController = navController,
                            startDestination = startDest!!
                        )
                        PoweredByBadge(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .imePadding()
                        )
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
