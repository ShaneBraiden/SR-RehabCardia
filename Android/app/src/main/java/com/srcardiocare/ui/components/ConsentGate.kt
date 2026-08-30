// ConsentGate.kt — The medical disclaimer and health-data disclosure a health
// app has to show before it collects anything.
package com.srcardiocare.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.R
import com.srcardiocare.core.consent.ConsentManager
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

/** Where the published legal documents live. Kept next to the screen that
 *  links to them so a hosting change is a one-line edit. */
object LegalLinks {
    const val PRIVACY_POLICY = "https://sr-cardiocare.web.app/privacy-policy.html"
    const val TERMS = "https://sr-cardiocare.web.app/terms-of-service.html"
    const val DELETE_ACCOUNT = "https://sr-cardiocare.web.app/delete-account.html"
}

/**
 * Wraps app content and holds it behind the disclaimer until the signed-in user
 * has accepted the current version.
 *
 * This sits *inside* the authenticated area rather than before login for two
 * reasons: consent belongs to a person, not a handset, and the disclosure names
 * who the data is shared with — which is only true once we know the account is
 * a patient of a particular clinic.
 *
 * When [uid] is null nothing is gated. There is no clinical content on the
 * login screen, and blocking it would strand a user who declined with no way
 * back in.
 */
@Composable
fun ConsentGate(
    uid: String?,
    onDecline: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (uid == null) {
        content()
        return
    }

    var accepted by remember(uid) {
        mutableStateOf(ConsentManager.hasAccepted(context, uid))
    }

    if (accepted) {
        content()
        return
    }

    DisclaimerScreen(
        onAccept = {
            // Local first: this is what unblocks the UI, and it must not depend
            // on a network the patient may not have.
            ConsentManager.markAccepted(context, uid)
            accepted = true
            scope.launch { UserRepository.recordConsent(ConsentManager.CURRENT_VERSION) }
        },
        onDecline = {
            ConsentManager.clear(context, uid)
            onDecline()
        }
    )
}

/**
 * The disclaimer itself.
 *
 * [onAccept] null puts the screen in read-only mode, which is how Settings
 * re-opens it for someone who wants to check what they agreed to.
 */
@Composable
fun DisclaimerScreen(
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Enabling "I have read this" only once the text has actually been scrolled
    // through is the difference between a record of consent and a record of a
    // tap. `maxValue` is 0 on a tall screen where it all fits, so a short
    // display is not penalised.
    val readToEnd by remember {
        derivedStateOf {
            scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue - 24
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = DesignTokens.Spacing.XL)
            ) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))

                Text(
                    text = stringResource(R.string.consent_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                Text(
                    text = stringResource(R.string.consent_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))

                Section(
                    heading = stringResource(R.string.consent_medical_heading),
                    body = stringResource(R.string.consent_medical_body)
                )

                // Given its own visual weight on purpose. The rest of this
                // screen is disclosure; this part is the one a patient may
                // need to remember mid-session.
                SafetySection(
                    heading = stringResource(R.string.consent_safety_heading),
                    body = stringResource(R.string.consent_safety_body)
                )

                Section(
                    heading = stringResource(R.string.consent_data_heading),
                    body = stringResource(R.string.consent_data_body)
                )

                TextButton(
                    onClick = { openUrl(context, LegalLinks.PRIVACY_POLICY) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        stringResource(R.string.consent_privacy_link),
                        color = DesignTokens.Colors.Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
            }

            // Actions stay pinned outside the scroll area so they are never the
            // reason someone misses the safety section above them.
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = DesignTokens.Spacing.XL,
                        vertical = DesignTokens.Spacing.MD
                    )
                ) {
                    if (onAccept != null) {
                        Button(
                            onClick = onAccept,
                            enabled = readToEnd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(DesignTokens.Radius.Base),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesignTokens.Colors.Primary
                            )
                        ) {
                            Text(
                                stringResource(R.string.consent_accept),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (!readToEnd) {
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                            Text(
                                stringResource(R.string.consent_scroll_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (onDecline != null) {
                        TextButton(
                            onClick = onDecline,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.consent_decline),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (onClose != null) {
                        Button(
                            onClick = onClose,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(DesignTokens.Radius.Base),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesignTokens.Colors.Primary
                            )
                        ) {
                            Text(stringResource(R.string.action_close), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(heading: String, body: String) {
    Text(
        text = heading,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
}

@Composable
private fun SafetySection(heading: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XS))
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
}

/** Opens a legal page in the browser; silent no-op on a device with none. */
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser installed. Nothing useful to fall back to.
    }
}
