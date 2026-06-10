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
import java.time.LocalDate

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
                val doctorName = when (role) {
                    "doctor" -> "Dr. $lastName"
                    "admin" -> "$firstName $lastName (Admin)"
                    else -> "$firstName $lastName"
                }

                // Admin sees ALL users; doctors see only their assigned patients
                val users = if (role == "admin") {
                    UserRepository.getAllUsers()
                } else {
                    UserRepository.getPatients(uid)
                }

                val patientRefs = users.filter { it.role.ifBlank { "patient" } == "patient" }
                val patientStatusMap = mutableMapOf<String, UserStatus>()
                val today = LocalDate.now().toString()

                coroutineScope {
                    patientRefs.map { patient ->
                        async {
                            try {
                                val assignments = AssignmentRepository.getAssignments(patient.id)
                                val status = when {
                                    assignments.isEmpty() -> UserStatus.INACTIVE
                                    else -> {
                                        val completedAssignmentsToday = assignments.count { assignment ->
                                            val dailyFrequency = assignment.dailyFrequency
                                            val completedSessionsToday = try {
                                                SessionRepository.getSessionsForDate(assignment.id, today).count {
                                                    it.status == SessionStatus.COMPLETED
                                                }
                                            } catch (_: Exception) {
                                                0
                                            }
                                            completedSessionsToday >= dailyFrequency
                                        }
                                        if (completedAssignmentsToday == assignments.size) UserStatus.ON_TRACK else UserStatus.NEEDS_ATTENTION
                                    }
                                }
                                patient.id to status
                            } catch (_: Exception) {
                                patient.id to UserStatus.INACTIVE
                            }
                        }
                    }.awaitAll().forEach { (id, status) ->
                        patientStatusMap[id] = status
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

                    val status = patientStatusMap[user.id] ?: UserStatus.ON_TRACK

                    UserItem(
                        id = user.id,
                        name = "$fName $lName".trim().ifBlank { "Unknown" },
                        subtitle = subtitle,
                        role = userRoleStr,
                        status = status,
                        isOnline = user.isOnline,
                        initials = initials.ifBlank { "?" }
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
