// DoctorProfileViewModel.kt — State + logic for the doctor/admin profile screen.
package com.srcardiocare.ui.screens.doctor

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

class DoctorProfileViewModel : ViewModel() {

    data class State(
        val firstName: String = "",
        val lastName: String = "",
        val email: String = "",
        val phone: String = "",
        val role: String = "",
        val licenseNumber: String = "",
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
                    _state.update {
                        it.copy(
                            firstName = user.firstName,
                            lastName = user.lastName,
                            email = user.email,
                            phone = user.phone ?: "",
                            role = user.role.replaceFirstChar { c -> c.uppercase() },
                            licenseNumber = user.licenseNumber ?: ""
                        )
                    }
                }
            } catch (_: Exception) { }
            _state.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Validates and persists profile edits. Validation failures are reported via
     * [onValidationError]; the success/error callbacks fire after the write.
     */
    fun save(
        editFirstName: String,
        editLastName: String,
        editPhone: String,
        editLicenseNumber: String,
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
                FirebaseService.updateUser(
                    mapOf(
                        "firstName" to editFirstName.trim(),
                        "lastName" to editLastName.trim(),
                        "phone" to phoneValidation.sanitizedValue,
                        "licenseNumber" to editLicenseNumber.trim()
                    )
                )
                _state.update {
                    it.copy(
                        firstName = editFirstName.trim(),
                        lastName = editLastName.trim(),
                        phone = phoneValidation.sanitizedValue,
                        licenseNumber = editLicenseNumber.trim()
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
