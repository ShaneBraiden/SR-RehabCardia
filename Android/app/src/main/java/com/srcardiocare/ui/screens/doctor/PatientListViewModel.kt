// PatientListViewModel.kt — State + loading for the doctor/admin patient list.
package com.srcardiocare.ui.screens.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.SessionRepository
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.data.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PatientListViewModel : ViewModel() {

    data class State(
        val allUsers: List<UserItem> = emptyList(),
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
                    _state.update { it.copy(isLoading = false, isRefreshing = false) }
                    return@launch
                }
                val role = UserRepository.getUser(uid).role

                val users = if (role == "admin") {
                    UserRepository.getAllUsers()
                } else {
                    UserRepository.getPatients(uid)
                }

                // Compute status based on today's assignment completion for patients
                val patientStatusMap = mutableMapOf<String, UserStatus>()
                // Rehab week counts from the patient's earliest assignment, so
                // it comes off the same read the status already needs — no
                // extra round trip per patient.
                val patientWeekMap = mutableMapOf<String, Int>()
                users.filter { it.role.ifBlank { "patient" } == "patient" }.forEach { patient ->
                    try {
                        val assignments =
                            AssignmentRepository.getAssignmentsFor(patient.id, uid, role)
                        rehabWeek(assignments)?.let { patientWeekMap[patient.id] = it }
                        patientStatusMap[patient.id] = when {
                            assignments.isEmpty() -> UserStatus.INACTIVE
                            else -> {
                                // One read per patient rather than one per
                                // assignment: today's sessions come back in a
                                // single query and the per-assignment tally is
                                // arithmetic, not I/O.
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
                    } catch (_: Exception) {
                        patientStatusMap[patient.id] = UserStatus.INACTIVE
                    }
                }

                val items = users.mapNotNull { user ->
                    val r = user.role.ifBlank { "patient" }
                    if (role != "admin" && r != "patient") return@mapNotNull null
                    if (user.id == uid) return@mapNotNull null

                    val name = user.fullName.ifBlank { user.email.ifBlank { "Unknown" } }
                    val initials = "${user.firstName.firstOrNull() ?: ""}${user.lastName.firstOrNull() ?: ""}".uppercase().ifBlank { "?" }
                    val isOnline = user.isOnline

                    val status = when (r) {
                        "admin", "doctor" -> UserStatus.ON_TRACK
                        // Doctor's manual care status (if set) overrides the auto-computed one.
                        else -> careStatusOverride(user.careStatus)
                            ?: patientStatusMap[user.id] ?: UserStatus.INACTIVE
                    }

                    val subtitle = when (r) {
                        "admin" -> "Administrator"
                        "doctor" -> user.speciality ?: "Doctor"
                        else -> user.injuries.firstOrNull() ?: user.primaryGoal ?: "Patient"
                    } + if (user.isBlocked) " • Blocked" else ""

                    val meta = if (r == "patient") {
                        patientMetaLine(
                            age = ageFrom(user.dateOfBirth),
                            sex = sexInitial(user.gender),
                            weekNumber = patientWeekMap[user.id]
                        )
                    } else ""

                    UserItem(user.id, name, subtitle, r, status, isOnline, initials, meta)
                }

                _state.update {
                    it.copy(
                        userRole = role,
                        allUsers = items,
                        errorMessage = null,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = ErrorHandler.getDisplayMessage(e, "load users"),
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }
}
