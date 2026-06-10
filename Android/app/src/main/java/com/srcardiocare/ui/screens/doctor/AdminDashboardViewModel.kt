// AdminDashboardViewModel.kt — State + loading for the admin dashboard.
package com.srcardiocare.ui.screens.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.SessionRepository
import com.srcardiocare.data.firebase.UserRepository
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

class AdminDashboardViewModel : ViewModel() {

    data class State(
        val doctors: List<DoctorItem> = emptyList(),
        val totalPatients: Int = 0,
        val totalUsers: Int = 0,
        val onlineCount: Int = 0,
        val blockedCount: Int = 0,
        val onTrackCount: Int = 0,
        val attentionCount: Int = 0,
        val notAssignedCount: Int = 0,
        val adminName: String = "",
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
                    _state.update { it.copy(errorMessage = "Not signed in.", isLoading = false, isRefreshing = false) }
                    return@launch
                }

                val currentUser = UserRepository.getUser(uid)
                val adminName = "${currentUser.fullName} (Admin)"

                val allUsers = UserRepository.getAllUsers()

                var patientCounter = 0
                var onlineCounter = 0
                var blockedCounter = 0
                val doctorList = mutableListOf<DoctorItem>()

                for (user in allUsers) {
                    val role = user.role.ifBlank { "patient" }
                    val isBlocked = user.isBlocked
                    val isOnline = user.isOnline

                    if (isOnline) onlineCounter++
                    if (isBlocked) blockedCounter++

                    when (role) {
                        "patient" -> patientCounter++
                        "doctor" -> {
                            val fName = user.firstName
                            val lName = user.lastName
                            val specialty = user.speciality ?: "General"
                            val initials = "${fName.firstOrNull() ?: ""}${lName.firstOrNull() ?: ""}".uppercase().ifBlank { "?" }

                            val assignedPatients = try {
                                FirebaseService.fetchPatients(user.id).size
                            } catch (_: Exception) { 0 }

                            doctorList.add(
                                DoctorItem(
                                    id = user.id,
                                    name = "Dr. $fName $lName".trim(),
                                    specialty = specialty,
                                    patientCount = assignedPatients,
                                    isOnline = isOnline,
                                    initials = initials
                                )
                            )
                        }
                    }
                }

                // Compute patient workout status (On Track / Attention / Not Assigned)
                val today = LocalDate.now().toString()
                val patientIds = allUsers.filter { it.role.ifBlank { "patient" } == "patient" }.map { it.id }

                var onTrack = 0
                var attention = 0
                var notAssigned = 0
                coroutineScope {
                    patientIds.map { patientId ->
                        async {
                            try {
                                val assignments = AssignmentRepository.getAssignments(patientId)
                                if (assignments.isEmpty()) return@async "not_assigned"
                                val completedAssignmentsToday = assignments.count { assignment ->
                                    val dailyFrequency = assignment.dailyFrequency
                                    val done = try {
                                        SessionRepository.getSessionsForDate(assignment.id, today).count {
                                            it.status == SessionStatus.COMPLETED
                                        }
                                    } catch (_: Exception) { 0 }
                                    done >= dailyFrequency
                                }
                                if (completedAssignmentsToday == assignments.size) "on_track" else "attention"
                            } catch (_: Exception) { "not_assigned" }
                        }
                    }.awaitAll().forEach { status ->
                        when (status) {
                            "on_track" -> onTrack++
                            "attention" -> attention++
                            else -> notAssigned++
                        }
                    }
                }

                _state.update {
                    it.copy(
                        adminName = adminName,
                        totalUsers = allUsers.size,
                        doctors = doctorList,
                        totalPatients = patientCounter,
                        onlineCount = onlineCounter,
                        blockedCount = blockedCounter,
                        onTrackCount = onTrack,
                        attentionCount = attention,
                        notAssignedCount = notAssigned,
                        errorMessage = null,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = ErrorHandler.getDisplayMessage(e, "load dashboard"),
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }
}
