// AssignExerciseScreen.kt — Doctor-side: pick an exercise from the library and
// prescribe it to a patient in one place (sets, reps, frequency, rest, duration,
// instructions). Writes the Assignment the patient app actually reads.
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val RestPresets = listOf(30, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignExerciseScreen(
    patientId: String,
    onBack: () -> Unit,
    onAssigned: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    var allExercises by remember { mutableStateOf<List<Pair<String, Map<String, Any?>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    // null = picking an exercise; non-null = configuring the prescription
    var selected by remember { mutableStateOf<Pair<String, Map<String, Any?>>?>(null) }

    // Prescription config
    var sets by remember { mutableIntStateOf(3) }
    var reps by remember { mutableIntStateOf(10) }
    var dailyFrequency by remember { mutableIntStateOf(1) }
    var restSeconds by remember { mutableIntStateOf(45) }
    var endDateInput by remember { mutableStateOf(LocalDate.now().plusDays(7).format(AssignmentFormDateFormat)) }
    var instructions by remember { mutableStateOf("") }
    var isAssigning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            allExercises = FirebaseService.fetchExercises()
        } catch (_: Exception) {
            allExercises = emptyList()
        }
        isLoading = false
    }

    fun selectExercise(item: Pair<String, Map<String, Any?>>) {
        val data = item.second
        sets = (data["sets"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 3
        reps = (data["reps"] as? Number)?.toInt()?.coerceIn(1, 99) ?: 10
        dailyFrequency = 1
        restSeconds = 45
        endDateInput = LocalDate.now().plusDays(7).format(AssignmentFormDateFormat)
        instructions = (data["instructions"] as? String) ?: ""
        selected = item
    }

    fun assign() {
        val item = selected ?: return
        val endDate = runCatching { LocalDate.parse(endDateInput, AssignmentFormDateFormat) }.getOrNull()
        if (endDate == null) {
            toast("Enter the end date as DD/MM/YYYY")
            return
        }
        if (!endDate.isAfter(LocalDate.now())) {
            toast("End date must be in the future")
            return
        }

        val data = item.second
        val exName = data["name"] as? String ?: data["title"] as? String ?: "Exercise"
        val expiryDateIso = endDate.toString()
        val expiryDays = ChronoUnit.DAYS.between(LocalDate.now(), endDate).toInt().coerceAtLeast(1)

        val exerciseData = mapOf<String, Any>(
            "exerciseId" to item.first,
            "name" to exName,
            "category" to (data["category"] as? String ?: ""),
            "difficulty" to (data["difficulty"] as? String ?: ""),
            "customSets" to sets,
            "customReps" to reps,
            "restSeconds" to restSeconds,
            "videoUrl" to (data["videoUrl"] as? String ?: ""),
            "instructions" to instructions.trim(),
            "assignedDate" to LocalDate.now().toString(),
            "expiryDate" to expiryDateIso
        )

        isAssigning = true
        scope.launch {
            try {
                FirebaseService.assignExerciseToPatientWithPrescription(
                    patientId = patientId,
                    exerciseData = exerciseData,
                    expiryDays = expiryDays,
                    expiryDate = expiryDateIso,
                    dailyFrequency = dailyFrequency
                )
                toast("Assigned: $exName")
                onAssigned()
            } catch (e: Exception) {
                toast("Failed to assign exercise")
                isAssigning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "Assign Exercise" else "Prescribe", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) selected = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (selected != null) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = { assign() },
                        enabled = !isAssigning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DesignTokens.Spacing.XL)
                            .height(56.dp),
                        shape = RoundedCornerShape(DesignTokens.Radius.Button),
                        colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary)
                    ) {
                        if (isAssigning) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                            Text("Assign Exercise", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val selectedItem = selected
        if (selectedItem == null) {
            ExercisePicker(
                modifier = Modifier.padding(padding),
                exercises = allExercises,
                isLoading = isLoading,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onPick = { selectExercise(it) }
            )
        } else {
            PrescriptionConfig(
                modifier = Modifier.padding(padding),
                data = selectedItem.second,
                sets = sets, onSets = { sets = it },
                reps = reps, onReps = { reps = it },
                dailyFrequency = dailyFrequency, onFrequency = { dailyFrequency = it },
                restSeconds = restSeconds, onRest = { restSeconds = it },
                endDateInput = endDateInput, onEndDate = { endDateInput = it },
                instructions = instructions, onInstructions = { instructions = it }
            )
        }
    }
}

@Composable
private fun ExercisePicker(
    modifier: Modifier,
    exercises: List<Pair<String, Map<String, Any?>>>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPick: (Pair<String, Map<String, Any?>>) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(DesignTokens.Radius.Input)
        )

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesignTokens.Colors.Primary)
            }

            exercises.isEmpty() -> EmptyLibrary()

            else -> {
                val filtered = exercises.filter { (_, d) ->
                    val name = d["name"] as? String ?: d["title"] as? String ?: ""
                    name.contains(searchQuery, ignoreCase = true)
                }
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No exercises match \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM)
                    ) {
                        items(filtered) { item -> ExercisePickRow(item) { onPick(item) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
            Text("No exercises in library", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Upload some exercise videos first", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExercisePickRow(item: Pair<String, Map<String, Any?>>, onClick: () -> Unit) {
    val data = item.second
    val name = data["name"] as? String ?: data["title"] as? String ?: "Unnamed Exercise"
    val category = data["category"] as? String ?: ""
    val difficulty = data["difficulty"] as? String ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.Base),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(DesignTokens.Radius.Base))
                    .background(DesignTokens.Colors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = DesignTokens.Colors.Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                val sub = listOfNotNull(category.ifBlank { null }, difficulty.ifBlank { null }).joinToString(" • ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.Add, contentDescription = "Select", tint = DesignTokens.Colors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PrescriptionConfig(
    modifier: Modifier,
    data: Map<String, Any?>,
    sets: Int, onSets: (Int) -> Unit,
    reps: Int, onReps: (Int) -> Unit,
    dailyFrequency: Int, onFrequency: (Int) -> Unit,
    restSeconds: Int, onRest: (Int) -> Unit,
    endDateInput: String, onEndDate: (String) -> Unit,
    instructions: String, onInstructions: (String) -> Unit
) {
    val exName = data["name"] as? String ?: data["title"] as? String ?: "Exercise"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.Spacing.XL)
    ) {
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
        ExerciseHeaderCard(name = exName, category = data["category"] as? String, difficulty = data["difficulty"] as? String)

        SectionTitle("Volume")
        StepperRow(label = "Sets per session", value = sets, min = 1, max = 20, onChange = onSets, icon = Icons.Default.Repeat)
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
        StepperRow(label = "Reps per set", value = reps, min = 1, max = 99, onChange = onReps, icon = Icons.Default.FitnessCenter)

        SectionTitle("Frequency")
        ChipPicker(
            label = "Times per day",
            options = listOf(1, 2, 3),
            selected = dailyFrequency,
            onSelect = onFrequency,
            renderOption = { "${it}×" }
        )

        SectionTitle("Rest between sets")
        ChipPicker(
            label = "Seconds",
            options = RestPresets,
            selected = restSeconds.takeIf { it in RestPresets } ?: -1,
            onSelect = onRest,
            renderOption = { "${it}s" }
        )

        SectionTitle("Duration")
        DateRow(label = "End date", value = endDateInput, onChange = onEndDate)
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
        val caption = runCatching {
            val end = LocalDate.parse(endDateInput, AssignmentFormDateFormat)
            val days = ChronoUnit.DAYS.between(LocalDate.now(), end)
            when {
                days < 0 -> "End date is in the past"
                days == 0L -> "Ends today"
                else -> "Starts today • $days-day plan"
            }
        }.getOrDefault("Enter the date as DD/MM/YYYY")
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Instructions for patient")
        OutlinedTextField(
            value = instructions,
            onValueChange = { onInstructions(it.take(500)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp),
            placeholder = { Text("e.g. Keep your back straight. Stop if you feel sharp pain.") },
            shape = RoundedCornerShape(DesignTokens.Radius.Input),
            supportingText = { Text("${instructions.length} / 500") }
        )

        Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
    }
}
