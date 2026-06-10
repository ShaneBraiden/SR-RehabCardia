// AssignmentListViewModel.kt — State + loading for the patient's daily exercises.
package com.srcardiocare.ui.screens.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.AssignmentRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.SessionRepository
import com.srcardiocare.data.model.ActiveExerciseItem
import com.srcardiocare.data.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class AssignmentListViewModel : ViewModel() {

    data class State(
        val activeExercises: List<ActiveExerciseItem> = emptyList(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val patientId = FirebaseService.currentUID
                if (patientId == null) {
                    _state.update { it.copy(isLoading = false, isRefreshing = false) }
                    return@launch
                }
                val today = LocalDate.now()
                val assignments = AssignmentRepository.getAssignments(patientId)
                val activeList = mutableListOf<ActiveExerciseItem>()

                for (assignment in assignments) {
                    val startDate = LocalDate.parse(assignment.startDate)
                    val endDate = LocalDate.parse(assignment.endDate)

                    // Skip if not yet started
                    if (today.isBefore(startDate)) continue

                    val allSessions = SessionRepository.getAllSessionsForAssignment(assignment.id)
                    val groupedByDate = allSessions.groupBy { it.sessionDate }

                    // Only today's status drives the active list (history has its own screen)
                    if (!today.isAfter(endDate)) {
                        val assignmentSessions = groupedByDate[today.toString()] ?: emptyList()
                        val completedSessions = assignmentSessions.count { it.status == SessionStatus.COMPLETED }
                        val inProgressSession = assignmentSessions.find { it.status == SessionStatus.IN_PROGRESS }

                        val isDone = completedSessions >= assignment.dailyFrequency
                        val daysUntilExpiry = ChronoUnit.DAYS.between(today, endDate).toInt()
                        val expiryText = when {
                            daysUntilExpiry == 0 -> "Expires today"
                            daysUntilExpiry == 1 -> "Expires tomorrow"
                            daysUntilExpiry <= 7 -> "Expires in $daysUntilExpiry days"
                            else -> "Expires ${endDate.format(dateFormatter)}"
                        }

                        activeList.add(
                            ActiveExerciseItem(
                                assignment = assignment,
                                sessionsToday = completedSessions,
                                dailyTarget = assignment.dailyFrequency,
                                isDoneToday = isDone,
                                canStartSession = !isDone && inProgressSession == null,
                                currentSession = inProgressSession,
                                expiryText = expiryText,
                                progressText = "$completedSessions/${assignment.dailyFrequency}"
                            )
                        )
                    }
                }

                _state.update {
                    it.copy(
                        activeExercises = activeList.sortedBy { item -> item.isDoneToday },
                        errorMessage = null,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = ErrorHandler.getDisplayMessage(e, "load assignments"),
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }
}
