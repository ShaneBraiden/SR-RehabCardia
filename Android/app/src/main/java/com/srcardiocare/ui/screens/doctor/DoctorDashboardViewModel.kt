// DoctorDashboardViewModel.kt — State + loading for the doctor/admin dashboard.
package com.srcardiocare.ui.screens.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.SessionRepository
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.data.firebase.WorkoutRepository
import com.srcardiocare.data.model.SessionStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientWorkoutStat(
    val patientId: String,
    val patientName: String,
    val completedSessions: Int,
    val totalSessions: Int,
    val lastCompletedAtMs: Long?
)

class DoctorDashboardViewModel : ViewModel() {

    data class State(
        val allUsers: List<UserItem> = emptyList(),
        val workoutStats: List<PatientWorkoutStat> = emptyList(),
        val doctorName: String = "",
        val userRole: String = "",
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val uid = FirebaseService.currentUID
                if (uid == null) {
                    _state.update {
                        it.copy(
                            errorMessage = "Not signed in. Please restart the app.",
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                    return@launch
                }
                val currentUser = UserRepository.getUser(uid)
                val firstName = currentUser.firstName
                val lastName = currentUser.lastName
                val role = currentUser.role
                val fullName = currentUser.fullName
                val doctorName = when (role) {
                    "doctor" -> {
                        // Full name: "Dr. <first> <last>". Surname-only reads as
                        // formal address, but on a dashboard the clinician is
                        // looking at their *own* record — seeing the whole name
                        // is how they confirm they are in the right account on a
                        // shared ward device. Falls back through whatever parts
                        // exist so we never render a bare "Dr.".
                        val nameForDoctor = listOf(firstName, lastName)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { fullName }
                        if (nameForDoctor.isBlank()) "Doctor" else "Dr. $nameForDoctor"
                    }
                    "admin" -> if (fullName.isBlank()) "Admin" else "$fullName (Admin)"
                    else -> fullName.ifBlank { "User" }
                }

                // Admin sees ALL users; doctors see only their assigned patients
                val users = if (role == "admin") {
                    UserRepository.getAllUsers()
                } else {
                    UserRepository.getPatients(uid)
                }

                val patientRefs = users.filter { it.role.ifBlank { "patient" } == "patient" }
                val patientStatusMap = mutableMapOf<String, UserStatus>()
                val patientWeekMap = mutableMapOf<String, Int>()

                coroutineScope {
                    patientRefs.map { patient ->
                        async {
                            try {
                                val assignments =
                                    AssignmentRepository.getAssignmentsFor(patient.id, uid, role)
                                val week = rehabWeek(assignments)
                                val status = when {
                                    assignments.isEmpty() -> UserStatus.INACTIVE
                                    else -> {
                                        // One query per patient, not one per
                                        // assignment. This used to fan out to
                                        // patients × assignments round trips on
                                        // every dashboard load *and* every
                                        // pull-to-refresh — a doctor with 30
                                        // patients on 4 exercises each paid 120
                                        // queries to colour some status dots.
                                        // Today's sessions for a patient are a
                                        // single indexed read; the per-assignment
                                        // split is arithmetic we can do locally.
                                        val completedToday = try {
                                            SessionRepository.getTodaysSessionsFor(patient.id, uid, role)
                                                .filter { it.status == SessionStatus.COMPLETED }
                                                .groupingBy { it.assignmentId }
                                                .eachCount()
                                        } catch (_: Exception) {
                                            emptyMap<String, Int>()
                                        }

                                        val completedAssignmentsToday = assignments.count { assignment ->
                                            (completedToday[assignment.id] ?: 0) >= assignment.dailyFrequency
                                        }
                                        if (completedAssignmentsToday == assignments.size) UserStatus.ON_TRACK else UserStatus.NEEDS_ATTENTION
                                    }
                                }
                                Triple(patient.id, status, week)
                            } catch (_: Exception) {
                                Triple(patient.id, UserStatus.INACTIVE, null)
                            }
                        }
                    }.awaitAll().forEach { (id, status, week) ->
                        patientStatusMap[id] = status
                        week?.let { patientWeekMap[id] = it }
                    }
                }

                val items = users.map { user ->
                    val fName = user.firstName
                    val lName = user.lastName
                    val userRoleStr = user.role.ifBlank { "patient" }
                    val injuries = user.injuries.firstOrNull() ?: ""
                    val initials = "${fName.firstOrNull() ?: ""}${lName.firstOrNull() ?: ""}".uppercase()

                    val subtitle = when (userRoleStr) {
                        "admin" -> "Administrator"
                        "doctor" -> user.speciality ?: "Doctor"
                        else -> injuries.ifBlank { "Patient" }
                    }

                    // Doctor's manual care status (if set) overrides the auto-computed one.
                    val status = careStatusOverride(user.careStatus)
                        ?: patientStatusMap[user.id] ?: UserStatus.ON_TRACK

                    UserItem(
                        id = user.id,
                        name = "$fName $lName".trim().ifBlank { "Unknown" },
                        subtitle = subtitle,
                        role = userRoleStr,
                        status = status,
                        isOnline = user.isOnline,
                        initials = initials.ifBlank { "?" },
                        meta = if (userRoleStr == "patient") {
                            patientMetaLine(
                                age = ageFrom(user.dateOfBirth),
                                sex = sexInitial(user.gender),
                                weekNumber = patientWeekMap[user.id]
                            )
                        } else ""
                    )
                }

                val patientWorkoutRefs = users.filter { it.role.ifBlank { "patient" } == "patient" }
                val stats = coroutineScope {
                    patientWorkoutRefs.map { patient ->
                        async {
                            val patientName = patient.fullName.ifBlank { "Unknown" }
                            try {
                                val workouts = WorkoutRepository.getWorkouts(patient.id)
                                val completedSessions = workouts.count { it.completedAtMs != null }
                                val totalSessions = workouts.size
                                val lastCompletedAt = workouts.mapNotNull { it.completedAtMs }.maxOrNull()
                                if (totalSessions > 0) {
                                    PatientWorkoutStat(
                                        patientId = patient.id,
                                        patientName = patientName,
                                        completedSessions = completedSessions,
                                        totalSessions = totalSessions,
                                        lastCompletedAtMs = lastCompletedAt
                                    )
                                } else {
                                    null
                                }
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }.awaitAll()
                        .filterNotNull()
                        .sortedByDescending { it.completedSessions }
                }

                _state.update {
                    it.copy(
                        userRole = role,
                        doctorName = doctorName,
                        allUsers = items,
                        workoutStats = stats,
                        errorMessage = null,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        workoutStats = emptyList(),
                        errorMessage = ErrorHandler.getDisplayMessage(e, "load data"),
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }
}
