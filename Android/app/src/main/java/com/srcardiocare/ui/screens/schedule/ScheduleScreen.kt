package com.srcardiocare.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.srcardiocare.R
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.components.tutorial.TutorialHelpButton
import com.srcardiocare.ui.components.tutorial.TutorialHost
import com.srcardiocare.ui.components.tutorial.TutorialIds
import com.srcardiocare.ui.components.tutorial.TutorialKeys
import com.srcardiocare.ui.components.tutorial.TutorialTours
import com.srcardiocare.ui.components.tutorial.tutorialTarget
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class DayItem(
    val dayName: String,
    val date: Int,
    val fullDate: LocalDate
)

data class SelectablePatient(
    val id: String,
    val name: String
)

data class ApptItem(
    val id: String,
    val time: String,
    val title: String,
    val type: String,
    val notes: String,
    val status: String,
    val color: Color,
    val apptDate: LocalDate?,
    val apptEpochMs: Long?,
    val patientId: String?,
    val doctorId: String?,
    val requestedByRole: String
)

/**
 * Day-strip window.
 *
 * This used to be the current calendar week only, which is why nothing more
 * than a couple of days out could be reached — from a Friday the strip ended on
 * Sunday. The strip now runs a month back (so past appointments stay
 * reviewable) and a year forward, and the header date picker can jump anywhere
 * inside the same window.
 */
private const val STRIP_DAYS_BEFORE = 30L
private const val STRIP_DAYS_AFTER = 365L

private fun scheduleDays(rangeStart: LocalDate): List<DayItem> =
    (0L..(STRIP_DAYS_BEFORE + STRIP_DAYS_AFTER)).map { offset ->
        val d = rangeStart.plusDays(offset)
        DayItem(
            dayName = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            date = d.dayOfMonth,
            fullDate = d
        )
    }

/**
 * Material's date picker works in UTC-midnight millis, while everything else
 * here is a [LocalDate]. These two convert between the pair without letting the
 * device time zone shift the day by one.
 */
private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Modal date picker constrained to [minDate]..[maxDate]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDatePickerDialog(
    initial: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val minMillis = remember(minDate) { minDate.toPickerMillis() }
    val maxMillis = remember(maxDate) { maxDate.toPickerMillis() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toPickerMillis(),
        yearRange = minDate.year..maxDate.year,
        selectableDates = remember(minMillis, maxMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis in minMillis..maxMillis

                override fun isSelectableYear(year: Int) =
                    year in minDate.year..maxDate.year
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(it.toPickerLocalDate()) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null
            ) {
                Text(stringResource(R.string.action_ok), color = DesignTokens.Colors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

/**
 * Modal 12-hour clock picker for the appointment time.
 *
 * `is24Hour = false` deliberately: appointments are rendered as "h:mm a"
 * everywhere else in the app, so 24-hour entry made the clinician do the
 * conversion in their head between typing a time and reading it back. The
 * keyboard toggle keeps fast numeric entry for anyone who preferred the old
 * two-field form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false
    )
    var keyboardEntry by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.schedule_pick_time), modifier = Modifier.weight(1f))
                IconButton(onClick = { keyboardEntry = !keyboardEntry }) {
                    Icon(
                        imageVector = if (keyboardEntry) Icons.Default.Schedule else Icons.Default.Keyboard,
                        contentDescription = stringResource(
                            if (keyboardEntry) R.string.schedule_time_switch_to_clock
                            else R.string.schedule_time_switch_to_keyboard
                        ),
                        tint = DesignTokens.Colors.Primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // `state.hour` stays 0..23 whichever mode is showing, so
                // nothing downstream has to know about the 12-hour display.
                if (keyboardEntry) TimeInput(state = state) else TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) {
                Text(stringResource(R.string.action_ok), color = DesignTokens.Colors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** How an appointment time is shown to the user - 12-hour, everywhere. */
private val ApptTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** Same clock, plus the day, for notification copy. */
private val ApptWhenFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a, dd/MM/yyyy")

/** Seeded into the form each time it opens. */
private val DefaultApptTime: LocalTime = LocalTime.of(10, 0)

/**
 * The only appointment type. Persisted to Firestore as `type` and interpolated
 * into notification copy, so it must stay in English — see
 * R.string.appointment_type_consultation for the display label.
 */
const val APPOINTMENT_TYPE_CONSULTATION = "Consultation"

fun statusColor(status: String): Color {
    return when (status.lowercase()) {
        "confirmed", "completed" -> DesignTokens.Colors.Success
        "cancelled" -> DesignTokens.Colors.Error
        "pending", "scheduled" -> DesignTokens.Colors.Warning
        else -> DesignTokens.Colors.Slate500
    }
}

/**
 * Maps the stored lowercase status key to a display label.
 *
 * The key itself is Firestore data and is never translated — only the label is.
 * An unrecognised key falls back to capitalising it, which keeps forward
 * compatibility if a new status is added server-side.
 */
@Composable
private fun prettyStatus(status: String): String = when (status.lowercase()) {
    "" -> stringResource(R.string.appt_status_unknown)
    "pending" -> stringResource(R.string.appt_status_pending)
    "confirmed" -> stringResource(R.string.appt_status_confirmed)
    "scheduled" -> stringResource(R.string.appt_status_scheduled)
    "completed" -> stringResource(R.string.appt_status_completed)
    "cancelled" -> stringResource(R.string.appt_status_cancelled)
    "accepted" -> stringResource(R.string.appt_status_accepted)
    "declined" -> stringResource(R.string.appt_status_declined)
    else -> status.replaceFirstChar { it.uppercase() }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val rangeStart = remember(today) { today.minusDays(STRIP_DAYS_BEFORE) }
    val rangeEnd = remember(today) { today.plusDays(STRIP_DAYS_AFTER) }
    val days = remember(rangeStart) { scheduleDays(rangeStart) }
    var selectedDate by remember { mutableStateOf(today) }
    val stripState = rememberLazyListState(initialFirstVisibleItemIndex = STRIP_DAYS_BEFORE.toInt())

    val viewModel: ScheduleViewModel = viewModel()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val appointments = ui.appointments
    val patients = ui.patients
    val isLoading = ui.isLoading
    val loadError = ui.loadError
    val userRole = ui.userRole
    val currentUid = ui.currentUid
    val assignedDoctorId = ui.assignedDoctorId

    var showAddDialog by remember { mutableStateOf(false) }
    var apptTime by remember { mutableStateOf(DefaultApptTime) }
    var apptNotes by remember { mutableStateOf("") }
    // The date being requested/booked. Independent of the day strip so the form
    // is self-contained — the strip only seeds its initial value.
    var apptDate by remember { mutableStateOf(today) }
    var showApptDatePicker by remember { mutableStateOf(false) }
    var showApptTimePicker by remember { mutableStateOf(false) }
    var showJumpDatePicker by remember { mutableStateOf(false) }
    var selectedPatientId by remember { mutableStateOf<String?>(null) }
    var patientQuery by remember { mutableStateOf("") }
    var patientMenuExpanded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val isClinician = userRole == "doctor" || userRole == "admin"

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }

    // Keep the selected day visible in the strip when it changes from the date
    // picker or the Today button.
    LaunchedEffect(selectedDate) {
        val index = days.indexOfFirst { it.fullDate == selectedDate }
        if (index >= 0) stripState.animateScrollToItem((index - 2).coerceAtLeast(0))
    }

    val filteredAppts = appointments.filter { it.apptDate == selectedDate }

    fun openAddDialog() {
        // A new appointment can never be in the past, so a past day in the strip
        // seeds today instead.
        apptDate = if (selectedDate.isBefore(today)) today else selectedDate
        apptTime = DefaultApptTime
        selectedPatientId = null
        patientQuery = ""
        patientMenuExpanded = false
        showAddDialog = true
    }

    suspend fun updateStatusWithNotification(
        appt: ApptItem,
        newStatus: String,
        notifyUserId: String?,
        title: String,
        body: String,
        action: String
    ) {
        if (notifyUserId.isNullOrBlank()) {
            throw Exception(context.getString(R.string.schedule_missing_target_user))
        }

        FirebaseService.updateAppointment(
            appt.id,
            mapOf(
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        com.srcardiocare.core.push.Notifier.send(
            com.srcardiocare.core.push.NotificationEvent.AppointmentUpdated(
                recipientId = notifyUserId,
                title = title,
                body = body,
                appointmentId = appt.id,
                action = action
            )
        )
    }

    if (showAddDialog) {
        if (showApptDatePicker) {
            ScheduleDatePickerDialog(
                initial = apptDate,
                // Neither a doctor booking nor a patient request may land in the past.
                minDate = today,
                maxDate = rangeEnd,
                onDismiss = { showApptDatePicker = false },
                onPick = { apptDate = it }
            )
        }

        if (showApptTimePicker) {
            ScheduleTimePickerDialog(
                initial = apptTime,
                onDismiss = { showApptTimePicker = false },
                onPick = { apptTime = it }
            )
        }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showAddDialog = false },
            title = {
                Text(
                    stringResource(
                        if (isClinician) R.string.schedule_new_appointment
                        else R.string.schedule_request_appointment
                    ),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isClinician) {
                        Text(stringResource(R.string.schedule_patient), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)

                        // An editable ExposedDropdownMenuBox: the doctor can either
                        // type to filter or just open the list of enrolled patients.
                        // (The previous read-only TextField swallowed taps, so the
                        // menu never opened and nothing could be typed either.)
                        val filteredPatients = remember(patientQuery, patients, selectedPatientId) {
                            val q = patientQuery.trim()
                            if (q.isBlank() || selectedPatientId != null) patients
                            else patients.filter { it.name.contains(q, ignoreCase = true) }
                        }

                        ExposedDropdownMenuBox(
                            expanded = patientMenuExpanded,
                            onExpandedChange = {
                                if (!isSaving && patients.isNotEmpty()) {
                                    patientMenuExpanded = !patientMenuExpanded
                                }
                            }
                        ) {
                            OutlinedTextField(
                                value = patientQuery,
                                onValueChange = { input ->
                                    patientQuery = input
                                    // Typing after a pick clears it — the field and the
                                    // selection must never disagree.
                                    selectedPatientId = null
                                    patientMenuExpanded = true
                                },
                                singleLine = true,
                                enabled = !isSaving && patients.isNotEmpty(),
                                label = { Text(stringResource(R.string.schedule_search_patient)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = patientMenuExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
                            )

                            ExposedDropdownMenu(
                                expanded = patientMenuExpanded,
                                onDismissRequest = { patientMenuExpanded = false }
                            ) {
                                if (filteredPatients.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.schedule_no_patient_match)) },
                                        onClick = {},
                                        enabled = false
                                    )
                                } else {
                                    filteredPatients.forEach { patient ->
                                        DropdownMenuItem(
                                            text = { Text(patient.name) },
                                            onClick = {
                                                selectedPatientId = patient.id
                                                patientQuery = patient.name
                                                patientMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (patients.isEmpty()) {
                            Text(
                                stringResource(R.string.schedule_no_patients),
                                style = MaterialTheme.typography.bodySmall,
                                color = DesignTokens.Colors.Warning
                            )
                        }
                    }

                    // ── Date ────────────────────────────────────────────────────
                    Text(
                        stringResource(
                            if (isClinician) R.string.schedule_date
                            else R.string.schedule_requested_date
                        ),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = { if (!isSaving) showApptDatePicker = true },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(DesignTokens.Radius.Base),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.schedule_pick_date),
                            tint = DesignTokens.Colors.Primary
                        )
                        Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                        Text(
                            apptDate.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy")),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(stringResource(R.string.schedule_time), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedButton(
                        onClick = { if (!isSaving) showApptTimePicker = true },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(DesignTokens.Radius.Base),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = stringResource(R.string.schedule_pick_time),
                            tint = DesignTokens.Colors.Primary
                        )
                        Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                        Text(
                            apptTime.format(ApptTimeFormatter),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = apptNotes,
                        onValueChange = { apptNotes = InputValidator.limitLength(it, InputValidator.MaxLength.NOTES) },
                        label = { Text(stringResource(R.string.schedule_notes_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(DesignTokens.Radius.Base),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val type = APPOINTMENT_TYPE_CONSULTATION
                        val dateTime = ZonedDateTime.of(apptDate, apptTime, ZoneId.systemDefault())

                        isSaving = true
                        scope.launch {
                            try {
                                if (apptDate.isBefore(LocalDate.now())) {
                                    throw Exception(context.getString(R.string.schedule_date_in_past))
                                }
                                val uid = currentUid ?: FirebaseService.currentUID
                                    ?: throw Exception(context.getString(R.string.schedule_not_signed_in))
                                val role = userRole

                                val appointmentData = mutableMapOf<String, Any>(
                                    "type" to type,
                                    "dateTime" to dateTime.toInstant().toString(),
                                    "notes" to apptNotes,
                                    "durationMinutes" to 30
                                )

                                if (role == "doctor" || role == "admin") {
                                    val patientId = selectedPatientId
                                    if (patientId.isNullOrBlank()) {
                                        throw Exception(context.getString(R.string.schedule_select_patient_error))
                                    }

                                    appointmentData["status"] = "confirmed"
                                    appointmentData["doctorId"] = uid
                                    appointmentData["patientId"] = patientId
                                    appointmentData["requestedByRole"] = if (role == "admin") "admin" else "doctor"

                                    val appointmentId = FirebaseService.createAppointment(appointmentData)
                                    com.srcardiocare.core.push.Notifier.send(
                                        com.srcardiocare.core.push.NotificationEvent.AppointmentScheduled(
                                            patientId = patientId,
                                            appointmentType = type,
                                            whenText = dateTime.format(ApptWhenFormatter),
                                            appointmentId = appointmentId
                                        )
                                    )

                                    snackbarHostState.showSnackbar(context.getString(R.string.schedule_appointment_created))
                                } else {
                                    val doctorId = assignedDoctorId
                                    if (doctorId.isNullOrBlank()) {
                                        throw Exception(context.getString(R.string.schedule_no_doctor_error))
                                    }

                                    appointmentData["status"] = "pending"
                                    appointmentData["doctorId"] = doctorId
                                    appointmentData["patientId"] = uid
                                    appointmentData["requestedByRole"] = "patient"

                                    val appointmentId = FirebaseService.createAppointment(appointmentData)
                                    com.srcardiocare.core.push.Notifier.send(
                                        com.srcardiocare.core.push.NotificationEvent.AppointmentRequested(
                                            doctorId = doctorId,
                                            appointmentType = type,
                                            whenText = dateTime.format(ApptWhenFormatter),
                                            appointmentId = appointmentId
                                        )
                                    )

                                    snackbarHostState.showSnackbar(context.getString(R.string.schedule_request_sent))
                                }

                                showAddDialog = false
                                apptNotes = ""
                                apptTime = DefaultApptTime
                                selectedPatientId = null
                                patientQuery = ""
                                // Jump the list to the day just booked, so the new
                                // appointment is visible instead of silently landing
                                // on a day the user isn't looking at.
                                selectedDate = apptDate
                                viewModel.load()
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(ErrorHandler.getDisplayMessage(e, "create appointment"))
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = DesignTokens.Colors.Primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            stringResource(
                                if (isClinician) R.string.schedule_create
                                else R.string.schedule_request
                            ),
                            color = DesignTokens.Colors.Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }, enabled = !isSaving) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showJumpDatePicker) {
        ScheduleDatePickerDialog(
            initial = selectedDate,
            minDate = rangeStart,
            maxDate = rangeEnd,
            onDismiss = { showJumpDatePicker = false },
            onPick = { selectedDate = it }
        )
    }

    TutorialHost(tourKey = TutorialKeys.SCHEDULE, steps = TutorialTours.schedule) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { selectedDate = today }) {
                        Text(stringResource(R.string.schedule_today), color = DesignTokens.Colors.Primary, fontWeight = FontWeight.SemiBold)
                    }
                    // Jump to any date in the window — the strip alone can't reach
                    // far-out dates comfortably.
                    IconButton(onClick = { showJumpDatePicker = true }) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.schedule_jump_to_date),
                            tint = DesignTokens.Colors.Primary
                        )
                    }
                    TutorialHelpButton()
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openAddDialog() },
                containerColor = DesignTokens.Colors.Primary,
                shape = CircleShape,
                modifier = Modifier.tutorialTarget(TutorialIds.SCHEDULE_ADD)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.schedule_new_appointment_desc), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyRow(
                state = stripState,
                contentPadding = PaddingValues(
                    horizontal = DesignTokens.Spacing.XL,
                    vertical = DesignTokens.Spacing.MD
                ),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM)
            ) {
                items(days.size) { index ->
                    val day = days[index]
                    val selected = day.fullDate == selectedDate

                    Card(
                        modifier = Modifier
                            .width(56.dp)
                            .then(
                                if (day.fullDate == today) Modifier.tutorialTarget(TutorialIds.SCHEDULE_CALENDAR)
                                else Modifier
                            )
                            .clickable { selectedDate = day.fullDate },
                        shape = RoundedCornerShape(DesignTokens.Radius.LG),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) DesignTokens.Colors.Primary else MaterialTheme.colorScheme.surface
                        ),
                        elevation = if (selected) CardDefaults.cardElevation(defaultElevation = 4.dp) else CardDefaults.cardElevation()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = DesignTokens.Spacing.MD),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                day.dayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${day.date}",
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy")),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM)
            )

            HorizontalDivider(color = DesignTokens.Colors.NeutralLight)
            when {
                isLoading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = DesignTokens.Spacing.MD)
                    ) {
                        items(5) { SkeletonListRow() }
                    }
                }

                loadError != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.schedule_load_error), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                loadError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.load() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }

                filteredAppts.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.schedule_empty), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.schedule_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.MD),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredAppts) { appt ->
                            Card(
                                shape = RoundedCornerShape(DesignTokens.Radius.LG),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(DesignTokens.Spacing.MD)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.width(72.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                appt.time,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = DesignTokens.Colors.Primary
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(40.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(appt.color)
                                        )

                                        Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(appt.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                            // "Consultation" is a value we write ourselves, so it
                                            // gets a display label; the stored string stays English.
                                            Text(
                                                if (appt.type == APPOINTMENT_TYPE_CONSULTATION)
                                                    stringResource(R.string.appointment_type_consultation)
                                                else appt.type,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (appt.notes.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(appt.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                                                .background(appt.color.copy(alpha = 0.12f))
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                prettyStatus(appt.status),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = appt.color
                                            )
                                        }
                                    }

                                    if (appt.status == "pending" && (userRole == "doctor" || userRole == "admin")) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            updateStatusWithNotification(
                                                                appt = appt,
                                                                newStatus = "confirmed",
                                                                notifyUserId = appt.patientId,
                                                                title = "Appointment request accepted",
                                                                body = "Your ${appt.type} request has been accepted.",
                                                                action = "accepted"
                                                            )
                                                            snackbarHostState.showSnackbar(context.getString(R.string.schedule_appointment_accepted))
                                                            viewModel.load()
                                                        } catch (e: Exception) {
                                                            snackbarHostState.showSnackbar(
                                                                ErrorHandler.getDisplayMessage(e, "accept appointment")
                                                            )
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text(stringResource(R.string.schedule_accept), color = DesignTokens.Colors.Success)
                                            }

                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            updateStatusWithNotification(
                                                                appt = appt,
                                                                newStatus = "cancelled",
                                                                notifyUserId = appt.patientId,
                                                                title = "Appointment request declined",
                                                                body = "Your ${appt.type} request was declined.",
                                                                action = "declined"
                                                            )
                                                            snackbarHostState.showSnackbar(context.getString(R.string.schedule_appointment_declined))
                                                            viewModel.load()
                                                        } catch (e: Exception) {
                                                            snackbarHostState.showSnackbar(
                                                                ErrorHandler.getDisplayMessage(e, "decline appointment")
                                                            )
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text(stringResource(R.string.schedule_decline), color = DesignTokens.Colors.Error)
                                            }
                                        }
                                    }

                                    if (appt.status == "pending" && userRole == "patient" && appt.requestedByRole == "patient") {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        updateStatusWithNotification(
                                                            appt = appt,
                                                            newStatus = "cancelled",
                                                            notifyUserId = appt.doctorId,
                                                            title = "Appointment request cancelled",
                                                            body = "Patient cancelled the ${appt.type} request.",
                                                            action = "cancelled"
                                                        )
                                                        snackbarHostState.showSnackbar(context.getString(R.string.schedule_request_cancelled))
                                                        viewModel.load()
                                                    } catch (e: Exception) {
                                                        snackbarHostState.showSnackbar(
                                                            ErrorHandler.getDisplayMessage(e, "cancel request")
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(stringResource(R.string.schedule_cancel_request), color = DesignTokens.Colors.Error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
