// PostWorkoutFeedbackScreen.kt — Feedback form after workout with Firestore save
package com.srcardiocare.ui.screens.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

@Composable
fun PostWorkoutFeedbackScreen(
    workoutId: String? = null,
    onSubmit: () -> Unit
) {
    var feltStress by remember { mutableStateOf<Boolean?>(null) }
    var feltStrain by remember { mutableStateOf<Boolean?>(null) }
    var respiratoryDifficulty by remember { mutableFloatStateOf(1f) }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var patientName by remember { mutableStateOf("Patient") }

    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    LaunchedEffect(Unit) {
        val uid = FirebaseService.currentUID ?: return@LaunchedEffect
        try {
            patientName = UserRepository.getUser(uid).fullName.ifBlank { "Patient" }
        } catch (_: Exception) {}
    }

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
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

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
                "Workout Complete!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Great job! Let us know how you felt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))

            // Felt Stress — Yes/No
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.LG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
                    Text("Did you feel stressed?", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        YesNoChip(
                            label = "Yes",
                            selected = feltStress == true,
                            onClick = { feltStress = true },
                            modifier = Modifier.weight(1f)
                        )
                        YesNoChip(
                            label = "No",
                            selected = feltStress == false,
                            onClick = { feltStress = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // Felt Strain — Yes/No
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.LG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
                    Text("Did you feel strain?", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        YesNoChip(
                            label = "Yes",
                            selected = feltStrain == true,
                            onClick = { feltStrain = true },
                            modifier = Modifier.weight(1f)
                        )
                        YesNoChip(
                            label = "No",
                            selected = feltStrain == false,
                            onClick = { feltStrain = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // Respiratory Difficulty — 1–10
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.LG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
                    Text("Respiratory Difficulty", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                    Slider(
                        value = respiratoryDifficulty,
                        onValueChange = { respiratoryDifficulty = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = DesignTokens.Colors.Primary,
                            activeTrackColor = DesignTokens.Colors.Primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Easy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${respiratoryDifficulty.toInt()}",
                            fontWeight = FontWeight.Bold,
                            color = DesignTokens.Colors.Primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Severe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                label = { Text("Typed Feedback / Notes (Sent to Doctor)") },
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
                                "stress" to feltStress,
                                "strain" to feltStrain,
                                "respiratoryDifficulty" to respiratoryDifficulty.toInt(),
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
                            
                            toast("Feedback submitted")
                            onSubmit()
                        } catch (e: Exception) {
                            toast("Failed to submit feedback")
                            errorMessage = ErrorHandler.getDisplayMessage(e, "save feedback")
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary),
                enabled = !isSubmitting && feltStress != null && feltStrain != null
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Submit Feedback", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
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
