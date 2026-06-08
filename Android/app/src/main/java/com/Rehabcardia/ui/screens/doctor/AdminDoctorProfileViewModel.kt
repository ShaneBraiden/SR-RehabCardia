// AdminDoctorProfileViewModel.kt — State + logic for the admin "manage doctor" screen.
package com.srcardiocare.ui.screens.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdminDoctorProfileViewModel : ViewModel() {

    data class State(
        val firstName: String = "",
        val lastName: String = "",
        val email: String = "",
        val phone: String = "",
        val speciality: String = "",
        val licenseNumber: String = "",
        val clinicName: String = "",
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val isDeleting: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadedId: String? = null

    fun loadOnce(doctorId: String, onError: (String) -> Unit) {
        if (loadedId == doctorId) return
        loadedId = doctorId
        viewModelScope.launch {
            try {
                val user = UserRepository.getUser(doctorId)
                _state.update {
                    it.copy(
                        firstName = user.firstName,
                        lastName = user.lastName,
                        email = user.email,
                        phone = user.phone ?: "",
                        speciality = user.speciality ?: "",
                        licenseNumber = user.licenseNumber ?: "",
                        clinicName = user.clinicName ?: ""
                    )
                }
            } catch (e: Exception) {
                onError(ErrorHandler.getDisplayMessage(e, "load doctor details"))
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun setFirstName(v: String) = _state.update { it.copy(firstName = v) }
    fun setLastName(v: String) = _state.update { it.copy(lastName = v) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v) }
    fun setSpeciality(v: String) = _state.update { it.copy(speciality = v) }
    fun setLicenseNumber(v: String) = _state.update { it.copy(licenseNumber = v) }
    fun setClinicName(v: String) = _state.update { it.copy(clinicName = v) }

    fun save(
        doctorId: String,
        onValidationError: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val s = _state.value
        if (s.firstName.isBlank() || s.lastName.isBlank()) {
            onValidationError("Names cannot be blank")
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                FirebaseService.updateUserById(
                    doctorId,
                    mapOf(
                        "firstName" to s.firstName.trim(),
                        "lastName" to s.lastName.trim(),
                        "phone" to s.phone.trim(),
                        "speciality" to s.speciality.trim(),
                        "licenseNumber" to s.licenseNumber.trim(),
                        "clinicName" to s.clinicName.trim()
                    )
                )
                onSuccess()
            } catch (e: Exception) {
                onError(ErrorHandler.getDisplayMessage(e, "update doctor"))
            }
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun delete(
        doctorId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                FirebaseService.deleteUser(doctorId)
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isDeleting = false) }
                onError(ErrorHandler.getDisplayMessage(e, "delete doctor"))
            }
        }
    }
}
