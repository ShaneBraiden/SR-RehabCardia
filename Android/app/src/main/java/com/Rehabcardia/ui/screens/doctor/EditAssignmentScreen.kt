// EditAssignmentScreen.kt — Doctor-side editor for a single Assignment
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.ShimmerBox
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DisplayDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                dailyFrequency = assignment.dailyFrequency.coerceIn(1, 3)
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
                        .height(56.dp),
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
                icon = Icons.Default.Repeat
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
            ChipPicker(
                label = "Times per day",
                options = listOf(1, 2, 3),
                selected = dailyFrequency,
                onSelect = { dailyFrequency = it },
                renderOption = { "${it}×" }
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

// ───────────────────────────────────────────────────────────────────────────────
// Building blocks
// ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExerciseHeaderCard(name: String, category: String?, difficulty: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Card),
        colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Primary.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.Spacing.LG),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(DesignTokens.Colors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                val sub = listOfNotNull(category, difficulty).joinToString(" • ")
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.4.sp
    )
    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.Base),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = DesignTokens.Colors.Primary)
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)

            IconButton(
                onClick = { if (value > min) onChange(value - 1) },
                enabled = value > min
            ) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease")
            }
            Text(
                "$value",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.widthIn(min = 36.dp),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = { if (value < max) onChange(value + 1) },
                enabled = value < max
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipPicker(
    label: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    renderOption: (Int) -> String,
    trailing: @Composable (() -> Unit)? = null
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { opt ->
                val isSelected = selected == opt
                Surface(
                    modifier = Modifier.clickable { onSelect(opt) },
                    shape = RoundedCornerShape(DesignTokens.Radius.Chip),
                    color = if (isSelected) DesignTokens.Colors.Primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Text(
                        renderOption(opt),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun DateRow(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(10)) },
        label = { Text(label) },
        placeholder = { Text("DD/MM/YYYY") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(DesignTokens.Radius.Input),
        leadingIcon = {
            Icon(Icons.Default.Event, contentDescription = null, tint = DesignTokens.Colors.Primary)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = value.isNotBlank() && runCatching { LocalDate.parse(value, DisplayDate) }.isFailure
    )
}
