// EditPatientViewModel.kt — State + logic for the clinician "edit patient details" screen.
package com.srcardiocare.ui.screens.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Same options, and the same stored values, as AddPatientScreen. */
val PATIENT_GENDERS = listOf("Male", "Female", "Other")

class EditPatientViewModel : ViewModel() {

    /** The editable values, as the form holds them. */
    data class Fields(
        val firstName: String = "",
        val lastName: String = "",
        val phone: String = "",
        val age: String = "",
        /** Index into [PATIENT_GENDERS]; -1 while unrecorded. */
        val genderIndex: Int = -1,
        val condition: String = ""
    )

    data class State(
        val email: String = "",
        val fields: Fields = Fields(),
        /** Snapshot as loaded — the save writes only what differs from it. */
        val initial: Fields = Fields(),
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val loadError: String? = null
    ) {
        val hasChanges: Boolean get() = fields != initial
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadedId: String? = null

    fun loadOnce(patientId: String) {
        if (loadedId == patientId) return
        loadedId = patientId
        viewModelScope.launch {
            try {
                val user = UserRepository.getUser(patientId)
                val fields = Fields(
                    firstName = user.firstName,
                    lastName = user.lastName,
                    phone = user.phone ?: "",
                    age = user.age?.toString() ?: "",
                    genderIndex = PATIENT_GENDERS.indexOfFirst { it.equals(user.gender?.trim(), ignoreCase = true) },
                    // Other clients may store several entries; shown joined so
                    // none are hidden, and only rewritten if the doctor edits it.
                    condition = user.injuries.joinToString(", ")
                )
                _state.update { it.copy(email = user.email, fields = fields, initial = fields, loadError = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loadError = ErrorHandler.getDisplayMessage(e, "load patient details")) }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun edit(transform: (Fields) -> Fields) = _state.update { it.copy(fields = transform(it.fields)) }

    fun setFirstName(v: String) = edit { it.copy(firstName = InputValidator.limitLength(v, InputValidator.MaxLength.NAME)) }
    fun setLastName(v: String) = edit { it.copy(lastName = InputValidator.limitLength(v, InputValidator.MaxLength.NAME)) }
    fun setPhone(v: String) = edit { it.copy(phone = InputValidator.normalizeMobile(v)) }
    fun setAge(v: String) {
        // Digits only, max 3 chars — same input contract as AddPatientScreen.
        if (v.all { it.isDigit() } && v.length <= 3) edit { it.copy(age = v) }
    }
    fun setGender(index: Int) = edit { it.copy(genderIndex = index) }
    fun setCondition(v: String) = edit { it.copy(condition = InputValidator.limitLength(v, InputValidator.MaxLength.INJURY_TYPE)) }

    fun save(
        patientId: String,
        onValidationError: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val s = _state.value
        if (s.isSaving) return
        if (!s.hasChanges) {
            onSuccess()
            return
        }
        val f = s.fields
        val initial = s.initial
        val changes = mutableMapOf<String, Any?>()

        if (f.firstName != initial.firstName) {
            val v = InputValidator.validateName(f.firstName, "First name")
            if (!v.isValid) return onValidationError(v.errorMessage ?: "Enter a valid first name")
            changes["firstName"] = v.sanitizedValue
        }
        if (f.lastName != initial.lastName) {
            // Optional: Add Patient splits a single "Full Name", so plenty of
            // records legitimately have no last name.
            if (f.lastName.isBlank()) {
                changes["lastName"] = ""
            } else {
                val v = InputValidator.validateName(f.lastName, "Last name")
                if (!v.isValid) return onValidationError(v.errorMessage ?: "Enter a valid last name")
                changes["lastName"] = v.sanitizedValue
            }
        }
        if (f.phone != initial.phone) {
            // Validated only when changed, so a legacy number stored in another
            // format does not block saving an unrelated field.
            val v = InputValidator.validateMobileNumber(f.phone)
            if (!v.isValid) return onValidationError(v.errorMessage ?: "Enter a valid mobile number")
            changes["phone"] = v.sanitizedValue
        }
        if (f.age != initial.age) {
            val v = InputValidator.validateAge(f.age)
            if (!v.isValid) return onValidationError(v.errorMessage ?: "Enter a valid age")
            changes["age"] = v.sanitizedValue.toIntOrNull() // blank clears it
        }
        if (f.genderIndex != initial.genderIndex && f.genderIndex in PATIENT_GENDERS.indices) {
            changes["gender"] = PATIENT_GENDERS[f.genderIndex]
        }
        if (f.condition != initial.condition) {
            val condition = f.condition.trim()
            changes["injuries"] = if (condition.isBlank()) emptyList<String>() else listOf(condition)
        }

        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                UserRepository.updatePatientDetails(patientId, changes)
                _state.update { it.copy(initial = it.fields) }
                onSuccess()
            } catch (e: Exception) {
                onError(ErrorHandler.getDisplayMessage(e, "update patient details"))
            }
            _state.update { it.copy(isSaving = false) }
        }
    }
}
