// PostWorkoutFeedbackScreen.kt — Feedback form after workout with Firestore save
package com.srcardiocare.ui.screens.feedback

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.srcardiocare.R
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.components.tutorial.TutorialHelpButton
import com.srcardiocare.ui.components.tutorial.TutorialHost
import com.srcardiocare.ui.components.tutorial.TutorialIds
import com.srcardiocare.ui.components.tutorial.TutorialKeys
import com.srcardiocare.ui.components.tutorial.TutorialTours
import com.srcardiocare.ui.components.tutorial.tutorialTarget
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

/**
 * Body locations offered when a patient reports pain.
 *
 * [value] is the canonical English string persisted to Firestore as
 * `painLocation`; [labelRes] is only what the patient sees. Keeping the two
 * separate means the doctor's view and any stored history stay English no
 * matter which language the patient's app is running in.
 */
private data class PainLocation(val value: String, @StringRes val labelRes: Int)

private val PAIN_LOCATIONS = listOf(
    PainLocation("Left Arm", R.string.pain_location_left_arm),
    PainLocation("Right Arm", R.string.pain_location_right_arm),
    PainLocation("Left Leg", R.string.pain_location_left_leg),
    PainLocation("Right Leg", R.string.pain_location_right_leg),
    PainLocation("Left Chest", R.string.pain_location_left_chest),
    PainLocation("Right Chest", R.string.pain_location_right_chest),
    PainLocation("Head", R.string.pain_location_head),
    PainLocation("Other", R.string.pain_location_other)
)

/** Colour for a Borg 0-10 rating: green (mild) -> orange (moderate) -> red (severe). */
private fun borgColor(value: Int) = when {
    value <= 3 -> DesignTokens.Colors.Success
    value <= 6 -> DesignTokens.Colors.Warning
    else -> DesignTokens.Colors.Error
}

@Composable
fun PostWorkoutFeedbackScreen(
    workoutId: String? = null,
    onSubmit: () -> Unit
) {
    var hadPain by remember { mutableStateOf<Boolean?>(null) }
    var painIntensity by remember { mutableFloatStateOf(0f) }
    var painLocation by remember { mutableStateOf<String?>(null) }
    var locationExpanded by remember { mutableStateOf(false) }
    var respiration by remember { mutableFloatStateOf(0f) }
    var pulseRate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var patientName by remember { mutableStateOf(context.getString(R.string.feedback_patient_fallback)) }

    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    LaunchedEffect(Unit) {
        val uid = FirebaseService.currentUID ?: return@LaunchedEffect
        try {
            patientName = UserRepository.getUser(uid).fullName.ifBlank { context.getString(R.string.feedback_patient_fallback) }
        } catch (_: Exception) {}
    }

    val pulseValue = pulseRate.toIntOrNull()
    val pulseValid = pulseValue != null && pulseValue in 30..250
    val painAnswered = hadPain != null
    val painComplete = hadPain == false || (hadPain == true && painLocation != null)
    val canSubmit = !isSubmitting && painAnswered && painComplete && pulseValid

    TutorialHost(tourKey = TutorialKeys.POST_WORKOUT_FEEDBACK, steps = TutorialTours.postWorkoutFeedback) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DesignTokens.Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Replay-tour control (no top bar on this screen).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TutorialHelpButton()
            }

            // Success icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DesignTokens.Colors.Success.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = DesignTokens.Colors.Success,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            Text(
                stringResource(R.string.feedback_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                stringResource(R.string.feedback_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))

            // ── Pain ──────────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.LG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
                    Text(stringResource(R.string.feedback_pain_question), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tutorialTarget(TutorialIds.FEEDBACK_PAIN),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        YesNoChip(
                            label = stringResource(R.string.action_yes),
                            selected = hadPain == true,
                            onClick = { hadPain = true },
                            modifier = Modifier.weight(1f)
                        )
                        YesNoChip(
                            label = stringResource(R.string.action_no),
                            selected = hadPain == false,
                            onClick = {
                                hadPain = false
                                painLocation = null
                                painIntensity = 0f
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (hadPain == true) {
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                        // Where is the pain? — dropdown
                        Text(stringResource(R.string.feedback_pain_where), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(DesignTokens.Radius.Base))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(DesignTokens.Radius.Base)
                                    )
                                    .clickable { locationExpanded = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    PAIN_LOCATIONS.firstOrNull { it.value == painLocation }
                                        ?.let { stringResource(it.labelRes) }
                                        ?: stringResource(R.string.feedback_select_location),
                                    color = if (painLocation == null) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = locationExpanded,
                                onDismissRequest = { locationExpanded = false }
                            ) {
                                PAIN_LOCATIONS.forEach { location ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(location.labelRes)) },
                                        onClick = {
                                            painLocation = location.value
                                            locationExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                        // Pain intensity — Borg scale 0-10
                        Text(stringResource(R.string.feedback_pain_intensity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        val painColor = borgColor(painIntensity.toInt())
                        Slider(
                            value = painIntensity,
                            onValueChange = { painIntensity = it },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = painColor,
                                activeTrackColor = painColor
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.feedback_pain_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${painIntensity.toInt()}",
                                fontWeight = FontWeight.Bold,
                                color = painColor,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(stringResource(R.string.feedback_pain_worst), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // ── Respiration — Borg scale 0-10 ───────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget(TutorialIds.FEEDBACK_BORG),
                shape = RoundedCornerShape(DesignTokens.Radius.LG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
                    Text(stringResource(R.string.feedback_respiration), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                    val respBorgColor = borgColor(respiration.toInt())
                    Slider(
                        value = respiration,
                        onValueChange = { respiration = it },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = respBorgColor,
                            activeTrackColor = respBorgColor
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.feedback_respiration_easy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${respiration.toInt()}",
                            fontWeight = FontWeight.Bold,
                            color = respBorgColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(stringResource(R.string.feedback_respiration_severe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // ── Pulse rate — entered by the patient ─────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget(TutorialIds.FEEDBACK_PULSE),
                shape = RoundedCornerShape(DesignTokens.Radius.LG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
                    Text(stringResource(R.string.feedback_pulse_rate), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                    OutlinedTextField(
                        value = pulseRate,
                        onValueChange = { input -> pulseRate = input.filter { it.isDigit() }.take(3) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.feedback_pulse_hint)) },
                        singleLine = true,
                        isError = pulseRate.isNotEmpty() && !pulseValid,
                        supportingText = {
                            if (pulseRate.isNotEmpty() && !pulseValid) {
                                Text(stringResource(R.string.feedback_pulse_error))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(DesignTokens.Radius.Base),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DesignTokens.Colors.Primary,
                            cursorColor = DesignTokens.Colors.Primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = InputValidator.limitLength(it, InputValidator.MaxLength.NOTES) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                label = { Text(stringResource(R.string.feedback_notes_label)) },
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DesignTokens.Colors.Primary,
                    cursorColor = DesignTokens.Colors.Primary
                )
            )

            // Error message
            errorMessage?.let {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                Text(it, color = DesignTokens.Colors.Error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            Button(
                onClick = {
                    isSubmitting = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val feedbackData = hashMapOf<String, Any?>(
                                "hadPain" to (hadPain == true),
                                "painIntensity" to if (hadPain == true) painIntensity.toInt() else 0,
                                "painLocation" to if (hadPain == true) painLocation else null,
                                "respiration" to respiration.toInt(),
                                "pulseRate" to pulseValue,
                                "notes" to notes.ifBlank { null },
                                "submittedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "patientId" to FirebaseService.currentUID
                            )
                            if (!workoutId.isNullOrBlank()) {
                                feedbackData["workoutId"] = workoutId
                            }
                            FirebaseService.submitPostWorkoutFeedback(feedbackData)

                            val uid = FirebaseService.currentUID
                            if (uid != null && notes.isNotBlank()) {
                                val chatText = "[Workout Feedback]\n$notes"
                                FirebaseService.sendChatMessage(uid, uid, patientName, chatText)
                            }

                            toast(context.getString(R.string.feedback_submitted))
                            onSubmit()
                        } catch (e: Exception) {
                            toast(context.getString(R.string.feedback_submit_failed))
                            errorMessage = ErrorHandler.getDisplayMessage(e, "save feedback")
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .tutorialTarget(TutorialIds.FEEDBACK_SUBMIT),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary),
                enabled = canSubmit
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.feedback_submit), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
        }
    }
    }
}

@Composable
private fun YesNoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.Base),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) DesignTokens.Colors.Primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(
            2.dp, DesignTokens.Colors.Primary
        ) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) DesignTokens.Colors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
