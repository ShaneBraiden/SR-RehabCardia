// AddDoctorScreen.kt — Admin-only form to register a new doctor
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.core.security.PasswordGenerator
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDoctorScreen(onSaved: () -> Unit, onBack: () -> Unit) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast = rememberToast()

    // Remember admin credentials to re-sign-in after creating the doctor
    var adminEmail by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val uid = FirebaseService.currentUID ?: return@LaunchedEffect
            adminEmail = UserRepository.getUser(uid).email.ifBlank { null }
        } catch (_: Exception) { }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Doctor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.XL)
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = InputValidator.limitLength(it, InputValidator.MaxLength.NAME) },
                label = { Text("Name") }, placeholder = { Text("e.g. Dr. Jane Smith") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            OutlinedTextField(
                value = email,
                onValueChange = { email = InputValidator.limitLength(it, InputValidator.MaxLength.EMAIL) },
                label = { Text("Email") }, placeholder = { Text("e.g. jane@clinic.com") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = InputValidator.limitLength(it, InputValidator.MaxLength.PHONE) },
                label = { Text("Phone") }, placeholder = { Text("e.g. +1 555 123 4567") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))

            // Password info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.PrimaryLight.copy(alpha = 0.3f))
            ) {
                Text(
                    "Default password: phone number + \"@srcardio\" (e.g. 0812345678@srcardio). Share it with the doctor — they will be prompted to change it on first login.",
                    modifier = Modifier.padding(DesignTokens.Spacing.MD),
                    style = MaterialTheme.typography.bodySmall,
                    color = DesignTokens.Colors.PrimaryDark
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            Button(
                onClick = {
                    // Validate name
                    val nameValidation = InputValidator.validateName(fullName, "Name")
                    if (!nameValidation.isValid) {
                        errorMessage = nameValidation.errorMessage
                        return@Button
                    }
                    val nameParts = nameValidation.sanitizedValue.split(" ", limit = 2)
                    val firstName = nameParts.firstOrNull() ?: ""
                    val lastName = if (nameParts.size > 1) nameParts[1] else ""

                    // Validate email
                    val emailValidation = InputValidator.validateEmail(email)
                    if (!emailValidation.isValid) {
                        errorMessage = emailValidation.errorMessage
                        return@Button
                    }

                    // Validate phone (optional but must be valid format if provided)
                    val phoneValidation = InputValidator.validatePhone(phone)
                    if (!phoneValidation.isValid) {
                        errorMessage = phoneValidation.errorMessage
                        return@Button
                    }

                    isLoading = true

                    scope.launch {
                        try {
                            // Default password is phone number + "@srcardio"; user must change on first login
                            val sanitizedPhone = phoneValidation.sanitizedValue.replace("\\s".toRegex(), "")
                            val temporaryPassword = if (sanitizedPhone.isNotBlank()) {
                                "${sanitizedPhone}@srcardio"
                            } else {
                                PasswordGenerator.generateTemporaryPassword()
                            }

                            // Create account WITHOUT switching auth session
                            val newDoctorUid = FirebaseService.registerOther(
                                email = emailValidation.sanitizedValue,
                                password = temporaryPassword,
                                firstName = firstName,
                                lastName = lastName,
                                role = "doctor"
                            )

                            // Write extra fields directly to the new doctor's doc
                            val extraFields = mutableMapOf<String, Any>()
                            if (phoneValidation.sanitizedValue.isNotBlank()) {
                                extraFields["phone"] = phoneValidation.sanitizedValue
                            }
                            // Flag for first login password change prompt
                            extraFields["mustChangePassword"] = true

                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(newDoctorUid)
                                .update(extraFields)
                                .await()

                            // Show success and navigate back
                            toast("Doctor added")
                            snackbarHostState.showSnackbar("Doctor account created! Share the temporary password with them securely.")
                            onSaved()
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = ErrorHandler.getDisplayMessage(e, "add doctor")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Save Doctor", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
        }
    }
}
