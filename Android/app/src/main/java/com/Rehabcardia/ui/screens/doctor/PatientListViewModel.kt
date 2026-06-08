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
import java.time.LocalDate

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
                val today = LocalDate.now().toString()
                users.filter { it.role.ifBlank { "patient" } == "patient" }.forEach { patient ->
                    try {
                        val assignments = AssignmentRepository.getAssignments(patient.id)
                        patientStatusMap[patient.id] = when {
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
                        else -> patientStatusMap[user.id] ?: UserStatus.INACTIVE
                    }

                    val subtitle = when (r) {
                        "admin" -> "Administrator"
                        "doctor" -> user.speciality ?: "Doctor"
                        else -> user.injuries.firstOrNull() ?: user.primaryGoal ?: "Patient"
                    } + if (user.isBlocked) " • Blocked" else ""

                    UserItem(user.id, name, subtitle, r, status, isOnline, initials)
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
