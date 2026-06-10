// PatientProfileSelfViewModel.kt — State + logic for the patient's own profile screen.
package com.srcardiocare.ui.screens.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PatientProfileSelfViewModel : ViewModel() {

    data class State(
        val firstName: String = "",
        val lastName: String = "",
        val email: String = "",
        val condition: String = "",
        val phone: String = "",
        val assignedDoctor: String = "",
        val isLoading: Boolean = true,
        val isSaving: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val uid = FirebaseService.currentUID
                if (uid != null) {
                    val user = UserRepository.getUser(uid)
                    val doctorId = user.assignedDoctorId
                    val assignedDoctor = if (doctorId != null) {
                        try {
                            val doctor = UserRepository.getUser(doctorId)
                            "Dr. ${doctor.lastName}".let { if (it == "Dr. ") doctor.fullName else it }
                        } catch (_: Exception) {
                            "Unknown"
                        }
                    } else {
                        "Not assigned"
                    }
                    _state.update {
                        it.copy(
                            firstName = user.firstName,
                            lastName = user.lastName,
                            email = user.email,
                            phone = user.phone ?: "",
                            condition = user.injuries.joinToString(", "),
                            assignedDoctor = assignedDoctor
                        )
                    }
                }
            } catch (_: Exception) { }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun save(
        editFirstName: String,
        editLastName: String,
        editPhone: String,
        editCondition: String,
        onValidationError: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val nameValidation = InputValidator.validateName(
            "${editFirstName.trim()} ${editLastName.trim()}".trim(),
            "Name"
        )
        if (!nameValidation.isValid) {
            onValidationError(nameValidation.errorMessage ?: "Invalid name")
            return
        }
        val phoneValidation = InputValidator.validatePhone(editPhone)
        if (!phoneValidation.isValid) {
            onValidationError(phoneValidation.errorMessage ?: "Invalid phone")
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val trimmedCondition = editCondition.trim()
                FirebaseService.updateUser(
                    mapOf(
                        "firstName" to editFirstName.trim(),
                        "lastName" to editLastName.trim(),
                        "phone" to phoneValidation.sanitizedValue,
                        "injuries" to if (trimmedCondition.isBlank()) emptyList<String>() else listOf(trimmedCondition)
                    )
                )
                _state.update {
                    it.copy(
                        firstName = editFirstName.trim(),
                        lastName = editLastName.trim(),
                        phone = phoneValidation.sanitizedValue,
                        condition = trimmedCondition
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                onError(ErrorHandler.getDisplayMessage(e, "update profile"))
            }
            _state.update { it.copy(isSaving = false) }
        }
    }
}
