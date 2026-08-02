// EditAssignmentScreen.kt — Doctor-side editor for a single Assignment
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.ShimmerBox
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.components.tutorial.TutorialHelpButton
import com.srcardiocare.ui.components.tutorial.TutorialHost
import com.srcardiocare.ui.components.tutorial.TutorialIds
import com.srcardiocare.ui.components.tutorial.TutorialKeys
import com.srcardiocare.ui.components.tutorial.TutorialTours
import com.srcardiocare.ui.components.tutorial.tutorialTarget
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DisplayDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAssignmentScreen(
    assignmentId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    // Original values (for change detection)
    var exerciseName by remember { mutableStateOf("") }
    var exerciseCategory by remember { mutableStateOf<String?>(null) }
    var exerciseDifficulty by remember { mutableStateOf<String?>(null) }
    var patientId by remember { mutableStateOf("") }

    // Editable fields
    var sets by remember { mutableIntStateOf(3) }
    var reps by remember { mutableIntStateOf(10) }
    var dailyFrequency by remember { mutableIntStateOf(1) }
    var restSeconds by remember { mutableIntStateOf(45) }
    var startDateInput by remember { mutableStateOf(LocalDate.now().format(DisplayDate)) }
    var endDateInput by remember { mutableStateOf(LocalDate.now().plusDays(7).format(DisplayDate)) }
    var instructions by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Load assignment
    LaunchedEffect(assignmentId) {
        try {
            val assignment = AssignmentRepository.getAssignmentById(assignmentId)
            if (assignment != null) {
                exerciseName = assignment.exerciseName
                exerciseCategory = assignment.exerciseCategory
                exerciseDifficulty = assignment.exerciseDifficulty
                patientId = assignment.patientId
                sets = assignment.sets.coerceIn(1, 20)
                reps = assignment.reps.coerceIn(1, 99)
                dailyFrequency = assignment.dailyFrequency.coerceIn(1, MAX_DAILY_FREQUENCY)
                restSeconds = assignment.restSeconds.coerceIn(5, 600)
                startDateInput = runCatching { LocalDate.parse(assignment.startDate).format(DisplayDate) }
                    .getOrDefault(startDateInput)
                endDateInput = runCatching { LocalDate.parse(assignment.endDate).format(DisplayDate) }
                    .getOrDefault(endDateInput)
                instructions = assignment.instructions ?: ""
            } else {
                errorMessage = "Assignment not found"
            }
        } catch (e: Exception) {
            errorMessage = ErrorHandler.getDisplayMessage(e, "load assignment")
        }
        isLoading = false
    }

    fun parseIso(input: String): String? = runCatching {
        LocalDate.parse(input, DisplayDate).toString()
    }.getOrNull()

    fun saveChanges() {
        val startIso = parseIso(startDateInput)
        val endIso = parseIso(endDateInput)
        if (startIso == null) {
            errorMessage = "Invalid start date (use DD/MM/YYYY)"
            return
        }
        if (endIso == null) {
            errorMessage = "Invalid end date (use DD/MM/YYYY)"
            return
        }
        if (LocalDate.parse(endIso).isBefore(LocalDate.parse(startIso))) {
            errorMessage = "End date must be after start date"
            return
        }

        isSaving = true
        scope.launch {
            try {
                FirebaseService.updateAssignment(
                    assignmentId,
                    mapOf(
                        "sets" to sets,
                        "reps" to reps,
                        "dailyFrequency" to dailyFrequency,
                        "restSeconds" to restSeconds,
                        "startDate" to startIso,
                        "endDate" to endIso,
                        "instructions" to instructions
                    )
                )
                successMessage = "Saved"
                toast("Assignment updated")
                onSaved()
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getDisplayMessage(e, "save assignment")
                toast("Failed to save assignment")
            }
            isSaving = false
        }
    }

    fun deleteAssignment() {
        isDeleting = true
        scope.launch {
            try {
                FirebaseService.deactivateAssignment(assignmentId)
                toast("Assignment removed")
                onDeleted()
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getDisplayMessage(e, "delete assignment")
                toast("Failed to remove assignment")
                isDeleting = false
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove assignment?") },
            text = { Text("This will stop the patient from seeing \"$exerciseName\" in their workouts. Past session history is preserved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteAssignment()
                    }
                ) { Text("Remove", color = DesignTokens.Colors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    TutorialHost(tourKey = TutorialKeys.EDIT_ASSIGNMENT, steps = TutorialTours.editAssignment) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Assignment", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !isLoading && !isSaving && !isDeleting
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Remove",
                            tint = DesignTokens.Colors.Error
                        )
                    }
                    TutorialHelpButton()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = ::saveChanges,
                    enabled = !isLoading && !isSaving && !isDeleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DesignTokens.Spacing.XL)
                        .height(56.dp)
                        .tutorialTarget(TutorialIds.EA_SAVE),
                    shape = RoundedCornerShape(DesignTokens.Radius.Button),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                        Text("Save Changes", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DesignTokens.Spacing.XL)
            ) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(DesignTokens.Radius.LG)
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                repeat(5) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(DesignTokens.Radius.Base)
                    )
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.XL)
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            ExerciseHeaderCard(
                name = exerciseName,
                category = exerciseCategory,
                difficulty = exerciseDifficulty
            )

            SectionTitle("Volume")
            StepperRow(
                label = "Sets per session",
                value = sets,
                min = 1, max = 20,
                onChange = { sets = it },
                icon = Icons.Default.Repeat,
                modifier = Modifier.tutorialTarget(TutorialIds.EA_SETS)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
            StepperRow(
                label = "Reps per set",
                value = reps,
                min = 1, max = 99,
                onChange = { reps = it },
                icon = Icons.Default.FitnessCenter
            )

            SectionTitle("Frequency")
            ExpandableCountPicker(
                label = "Times per day",
                value = dailyFrequency,
                onSelect = { dailyFrequency = it },
                baseOptions = listOf(1, 2, 3),
                max = MAX_DAILY_FREQUENCY,
                renderOption = { "${it}×" },
                modifier = Modifier.tutorialTarget(TutorialIds.EA_FREQUENCY)
            )

            SectionTitle("Rest between sets")
            ChipPicker(
                label = "Seconds",
                options = listOf(15, 30, 45, 60, 90, 120),
                selected = restSeconds.takeIf { it in setOf(15, 30, 45, 60, 90, 120) } ?: -1,
                onSelect = { restSeconds = it },
                renderOption = { "${it}s" },
                trailing = {
                    OutlinedTextField(
                        value = if (restSeconds in setOf(15, 30, 45, 60, 90, 120)) "" else restSeconds.toString(),
                        onValueChange = { raw ->
                            val v = raw.filter { it.isDigit() }.take(3).toIntOrNull()
                            if (v != null) restSeconds = v.coerceIn(5, 600)
                        },
                        placeholder = { Text("Custom") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp),
                        shape = RoundedCornerShape(DesignTokens.Radius.Input),
                        trailingIcon = { Text("s", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
            )

            SectionTitle("Schedule")
            DateRow(
                label = "Start date",
                value = startDateInput,
                onChange = { startDateInput = it }
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
            DateRow(
                label = "End date",
                value = endDateInput,
                onChange = { endDateInput = it }
            )

            SectionTitle("Instructions for patient")
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it.take(500) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp),
                placeholder = { Text("e.g. Keep your back straight. Stop if you feel sharp pain.") },
                shape = RoundedCornerShape(DesignTokens.Radius.Input),
                supportingText = { Text("${instructions.length} / 500") }
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            errorMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = DesignTokens.Spacing.MD),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = CardDefaults.cardColors(
                        containerColor = DesignTokens.Colors.Error.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(DesignTokens.Spacing.MD),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = DesignTokens.Colors.Error
                        )
                        Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                        Text(msg, color = DesignTokens.Colors.Error, modifier = Modifier.weight(1f))
                        IconButton(onClick = { errorMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            successMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = DesignTokens.Spacing.MD),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = CardDefaults.cardColors(
                        containerColor = DesignTokens.Colors.Success.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(DesignTokens.Spacing.MD),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = DesignTokens.Colors.Success
                        )
                        Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                        Text(msg, color = DesignTokens.Colors.Success)
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
        }
    }
    }
}

// Shared form building blocks (ExerciseHeaderCard, SectionTitle, StepperRow,
// ChipPicker, DateRow) now live in AssignmentFormComponents.kt so AssignExerciseScreen
// can reuse them.
