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

    /** Which field failed validation, so the screen can pick the right message. */
    enum class ProfileField { NAME, PHONE }

    /** Outcome of resolving the patient's assigned doctor. */
    sealed interface AssignedDoctor {
        data class Named(val name: String) : AssignedDoctor
        /** Doctor id present on the user doc but the lookup failed. */
        data object Unknown : AssignedDoctor
        data object NotAssigned : AssignedDoctor
    }

    data class State(
        val firstName: String = "",
        val lastName: String = "",
        val email: String = "",
        val condition: String = "",
        val phone: String = "",
        // Not a pre-rendered string: "Not assigned" / "Unknown" are UI copy and
        // must be resolved against the current locale by the composable.
        val assignedDoctor: AssignedDoctor = AssignedDoctor.NotAssigned,
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
                            val name = "Dr. ${doctor.lastName}"
                                .let { if (it == "Dr. ") doctor.fullName else it }
                            AssignedDoctor.Named(name)
                        } catch (_: Exception) {
                            AssignedDoctor.Unknown
                        }
                    } else {
                        AssignedDoctor.NotAssigned
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
        nameFieldLabel: String,
        onValidationError: (ProfileField, String?) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val nameValidation = InputValidator.validateName(
            "${editFirstName.trim()} ${editLastName.trim()}".trim(),
            nameFieldLabel
        )
        if (!nameValidation.isValid) {
            onValidationError(ProfileField.NAME, nameValidation.errorMessage)
            return
        }
        val phoneValidation = InputValidator.validatePhone(editPhone)
        if (!phoneValidation.isValid) {
            onValidationError(ProfileField.PHONE, phoneValidation.errorMessage)
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                // Condition (injuries) is doctor-managed and intentionally not written here.
                FirebaseService.updateUser(
                    mapOf(
                        "firstName" to editFirstName.trim(),
                        "lastName" to editLastName.trim(),
                        "phone" to phoneValidation.sanitizedValue
                    )
                )
                _state.update {
                    it.copy(
                        firstName = editFirstName.trim(),
                        lastName = editLastName.trim(),
                        phone = phoneValidation.sanitizedValue
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
