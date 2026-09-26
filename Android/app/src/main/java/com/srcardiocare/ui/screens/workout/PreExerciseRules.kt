// PreExerciseRules.kt — The safety rules the patient sees before every exercise
// session.
//
// Content is taken from the clinic's booklet "Cardiac Rehabilitation Phase II —
// Home-based Physical activities: Guidelines" (Sri Ramachandra, Faculty of
// Physiotherapy): the Exercise Counselling DOs/DON'Ts, the sternal precautions,
// and the warning signs that end a session. It is deliberately the same advice
// the patient was given on paper — if the booklet is revised, revise these
// strings, do not paraphrase them here.
//
// Shown on every session rather than once per account. That is the clinic's
// call: the warning signs are the part a patient needs in working memory while
// they are exercising, not something to have read in week one. The primary
// button is therefore enabled immediately — unlike the one-time consent gate,
// which holds it until the text has been scrolled — because a screen that
// punishes a patient three times a day is a screen they learn to resent.
package com.srcardiocare.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.R
import com.srcardiocare.ui.theme.DesignTokens

/**
 * Full-screen safety brief shown ahead of a workout.
 *
 * [onAcknowledge] moves on to the exercise; [onBack] leaves without starting
 * one. Nothing is written to Firestore from here — the caller does not start
 * the session until the patient has acknowledged, so backing out of this
 * screen leaves no half-finished session behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreExerciseRulesScreen(
    exerciseName: String,
    onAcknowledge: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.rules_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            exerciseName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.rules_leave_desc)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = DesignTokens.Spacing.Base)
            ) {
                Text(
                    text = stringResource(R.string.rules_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.Base))

                // First on the page, and the only card in the error colour.
                // A patient who reads nothing else should still have read this.
                RuleCard(
                    icon = Icons.Filled.Warning,
                    heading = stringResource(R.string.rules_stop_heading),
                    bullets = stringArrayResource(R.array.rules_stop_items),
                    container = MaterialTheme.colorScheme.errorContainer,
                    onContainer = MaterialTheme.colorScheme.onErrorContainer,
                    footer = stringResource(R.string.rules_stop_footer)
                )

                RuleCard(
                    icon = Icons.Filled.CheckCircle,
                    heading = stringResource(R.string.rules_do_heading),
                    bullets = stringArrayResource(R.array.rules_do_items),
                    container = MaterialTheme.colorScheme.surface,
                    onContainer = MaterialTheme.colorScheme.onSurface,
                    iconTint = DesignTokens.Colors.Success
                )

                RuleCard(
                    icon = Icons.Filled.NotInterested,
                    heading = stringResource(R.string.rules_dont_heading),
                    bullets = stringArrayResource(R.array.rules_dont_items),
                    container = MaterialTheme.colorScheme.surface,
                    onContainer = MaterialTheme.colorScheme.onSurface,
                    iconTint = DesignTokens.Colors.Warning
                )

                // Kept for everyone rather than hidden behind a flag: the
                // assignment carries no record of whether this patient has had
                // surgery, so the heading states the condition instead.
                RuleCard(
                    icon = Icons.Filled.Favorite,
                    heading = stringResource(R.string.rules_sternal_heading),
                    bullets = stringArrayResource(R.array.rules_sternal_items),
                    container = MaterialTheme.colorScheme.surface,
                    onContainer = MaterialTheme.colorScheme.onSurface,
                    iconTint = DesignTokens.Colors.Primary
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.Base))
            }

            // Pinned below the scroll area so the stop-signs above it are never
            // skipped past by a patient reaching for the button.
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = DesignTokens.Spacing.Base,
                            vertical = DesignTokens.Spacing.MD
                        )
                        .height(52.dp),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignTokens.Colors.Primary
                    )
                ) {
                    Text(
                        stringResource(R.string.rules_acknowledge),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    icon: ImageVector,
    heading: String,
    bullets: Array<String>,
    container: Color,
    onContainer: Color,
    footer: String? = null,
    iconTint: Color = onContainer
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        color = container
    ) {
        Column(modifier = Modifier.padding(DesignTokens.Spacing.Base)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            bullets.forEachIndexed { index, bullet ->
                if (index > 0) Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(iconTint)
                    )
                    Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer
                    )
                }
            }

            if (footer != null) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                Text(
                    text = footer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
}
