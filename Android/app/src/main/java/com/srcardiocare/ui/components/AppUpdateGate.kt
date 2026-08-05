// AppUpdateGate.kt — Blocks or nudges builds that are behind the release gate.
package com.srcardiocare.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.srcardiocare.data.firebase.AppVersionRepository
import com.srcardiocare.data.firebase.AppVersionRepository.UpdateStatus
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

/**
 * Wraps the app content and enforces the release gate read from
 * `config/appVersion`.
 *
 * Two tiers, and the difference is deliberate:
 *
 *  - **Required** — a full-screen blocker with no dismiss and no back-out.
 *    Returning from the Play Store without actually installing re-runs the
 *    check on resume and puts the blocker straight back. A gate that unlocks
 *    on *visiting* the store rather than on *updating* is a gate any user
 *    clears in two taps, which defeats the point of having a kill switch for a
 *    build that is mishandling clinical data.
 *  - **Optional** — a dismissible dialog with "Maybe later". Dismissal is
 *    remembered for the session so it does not re-appear every time the app is
 *    foregrounded, which is what turns a helpful prompt into a nag.
 *
 * The check re-runs on every resume, so a requirement raised while the app sits
 * in the background takes effect without a cold start.
 */
@Composable
fun AppUpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var status by remember { mutableStateOf<UpdateStatus>(UpdateStatus.UpToDate) }
    // Session-scoped: "Maybe later" silences the optional prompt until the
    // process restarts, not forever.
    var optionalDismissed by remember { mutableStateOf(false) }

    fun refresh(force: Boolean = false) {
        scope.launch {
            // The resume-driven check is allowed to answer from cache; an
            // explicit "I've already updated" is the one case where the user is
            // telling us the cached answer is stale.
            if (force) AppVersionRepository.invalidateCache()
            val result = AppVersionRepository.checkForUpdate(context)
            // A newly-raised floor must override a dismissal — "later" was
            // consent to skip a suggestion, not to ignore a hard requirement.
            if (result is UpdateStatus.Required) optionalDismissed = false
            status = result
        }
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // `content()` is invoked from exactly one call site.
    //
    // It used to appear in both the Optional and the UpToDate branch of a
    // `when`. Those are two different positions in the composition, so every
    // flip between "an update is available" and "you're up to date" — and the
    // check re-runs on each resume — moved the entire app subtree, disposing it
    // and every `remember` inside it. Screens lost their state and gates that
    // had already been satisfied composed again from scratch. The optional
    // dialog is now an overlay instead, which is what it always was visually.
    val required = status as? UpdateStatus.Required

    if (required != null) {
        UpdateRequiredScreen(
            message = required.message,
            onUpdate = { openPlayStore(context, required.storeUrl) },
            onRecheck = { refresh(force = true) },
        )
    } else {
        content()
    }

    val optional = status as? UpdateStatus.Optional
    if (optional != null && !optionalDismissed) {
        UpdateAvailableDialog(
            message = optional.message,
            onUpdate = {
                optionalDismissed = true
                openPlayStore(context, optional.storeUrl)
            },
            onDismiss = { optionalDismissed = true },
        )
    }
}

/** Full-screen blocker. No back handler is registered on purpose — the system
 *  back gesture leaves the app entirely, which is a legitimate exit; what is
 *  not available is reaching app content behind this screen. */
@Composable
private fun UpdateRequiredScreen(
    message: String,
    onUpdate: () -> Unit,
    onRecheck: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(DesignTokens.Spacing.XL),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = DesignTokens.Colors.Primary,
                modifier = Modifier.size(64.dp),
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))

            Text(
                text = "Update Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            Button(
                onClick = onUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignTokens.Colors.Primary,
                ),
            ) {
                Text("Update Now", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))

            // For the user who has already updated through the Play Store app
            // and does not want to wait for the next resume event.
            TextButton(onClick = onRecheck) {
                Text("I've already updated — check again")
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            Text(
                text = "Installed version " +
                    AppVersionRepository.installedVersionName(context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Dismissible prompt for a release that is available but not mandatory. */
@Composable
private fun UpdateAvailableDialog(
    message: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Available", fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(
                    "Update",
                    color = DesignTokens.Colors.Primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Maybe later") }
        },
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
    )
}

/**
 * Opens the Play Store listing, preferring the installed Play app over a
 * browser. Devices without Play Services (and sideloaded installs, which is how
 * the clinic tests builds) fall back to the web listing rather than crashing on
 * a missing activity.
 */
private fun openPlayStore(context: Context, storeUrl: String) {
    val marketUri = Uri.parse("market://details?id=${context.packageName}")
    val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(marketIntent)
        return
    } catch (_: ActivityNotFoundException) {
        // No Play Store app installed — fall through to the browser.
    }

    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(webIntent)
    } catch (_: ActivityNotFoundException) {
        // No browser either. Nothing further to offer; the blocker stays up.
    }
}
