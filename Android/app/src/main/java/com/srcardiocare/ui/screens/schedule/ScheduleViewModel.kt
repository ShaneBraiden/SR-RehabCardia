// ScheduleViewModel.kt — State + loading for the appointments schedule.
package com.srcardiocare.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.AppointmentRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ScheduleViewModel : ViewModel() {

    data class State(
        val appointments: List<ApptItem> = emptyList(),
        val patients: List<SelectablePatient> = emptyList(),
        val userRole: String = "",
        val currentUid: String? = null,
        val assignedDoctorId: String? = null,
        val isLoading: Boolean = true,
        val loadError: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    private suspend fun resolveUserName(uid: String?, cache: MutableMap<String, String>): String {
        if (uid.isNullOrBlank()) return "Unknown"
        cache[uid]?.let { return it }
        return try {
            val user = UserRepository.getUser(uid)
            val base = user.fullName.ifBlank { "Unknown" }
            val result = if (user.role == "doctor") "Dr. $base" else base
            cache[uid] = result
            result
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val uid = FirebaseService.currentUID
                if (uid == null) {
                    _state.update {
                        it.copy(loadError = "Not signed in", appointments = emptyList(), patients = emptyList(), isLoading = false)
                    }
                    return@launch
                }

                val currentUser = UserRepository.getUser(uid)
                val role = currentUser.role.ifBlank { "patient" }

                val patients = if (role == "doctor") {
                    UserRepository.getPatients(uid).map { patient ->
                        SelectablePatient(patient.id, patient.fullName.ifBlank { "Unknown" })
                    }.sortedBy { it.name }
                } else if (role == "admin") {
                    UserRepository.getAllPatients().map { patient ->
                        SelectablePatient(patient.id, patient.fullName.ifBlank { "Unknown" })
                    }.sortedBy { it.name }
                } else {
                    emptyList()
                }

                val rawAppts = try {
                    AppointmentRepository.getAppointments(uid, role)
                } catch (_: Exception) {
                    AppointmentRepository.getAppointmentsUnordered(uid, role)
                }

                val nameCache = mutableMapOf<String, String>()
                patients.forEach { nameCache[it.id] = it.name }

                val appts = rawAppts.map { appt ->
                    val zdt = appt.dateTimeMs?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                    }
                    val time = zdt?.format(timeFormatter) ?: ""
                    val patientId = appt.patientId
                    val doctorId = appt.doctorId

                    val title = if (role == "doctor" || role == "admin") {
                        resolveUserName(patientId, nameCache)
                    } else {
                        resolveUserName(doctorId, nameCache)
                    }

                    ApptItem(
                        id = appt.id,
                        time = time,
                        title = title,
                        type = appt.type,
                        notes = appt.notes,
                        status = appt.status,
                        color = statusColor(appt.status),
                        apptDate = zdt?.toLocalDate(),
                        apptEpochMs = appt.dateTimeMs,
                        patientId = patientId,
                        doctorId = doctorId,
                        requestedByRole = appt.requestedByRole
                    )
                }.sortedBy { it.apptEpochMs ?: Long.MAX_VALUE }

                _state.update {
                    it.copy(
                        currentUid = uid,
                        userRole = role,
                        assignedDoctorId = currentUser.assignedDoctorId,
                        patients = patients,
                        appointments = appts,
                        loadError = null,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loadError = ErrorHandler.getDisplayMessage(e, "load schedule"), isLoading = false)
                }
            }
        }
    }
}
