// ChangePasswordScreen.kt — Enter old password, set new password
package com.srcardiocare.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.srcardiocare.R
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.core.security.PasswordValidator
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_change_password), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            Text(
                stringResource(R.string.change_password_heading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
            Text(
                stringResource(R.string.change_password_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))

            // Old password
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it; errorMessage = null },
                label = { Text(stringResource(R.string.change_password_current_label)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
                        Icon(
                            if (oldPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (oldPasswordVisible) R.string.action_hide else R.string.action_show
                            )
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DesignTokens.Colors.Primary,
                    cursorColor = DesignTokens.Colors.Primary
                )
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // New password
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; errorMessage = null },
                label = { Text(stringResource(R.string.change_password_new_label)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(
                            if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (newPasswordVisible) R.string.action_hide else R.string.action_show
                            )
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DesignTokens.Colors.Primary,
                    cursorColor = DesignTokens.Colors.Primary
                )
            )

            // Password requirements hint
            Text(
                PasswordValidator.getRequirementsText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // Confirm password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMessage = null },
                label = { Text(stringResource(R.string.change_password_confirm_label)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DesignTokens.Colors.Primary,
                    cursorColor = DesignTokens.Colors.Primary
                )
            )

            // Error
            errorMessage?.let {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                Text(it, color = DesignTokens.Colors.Error, style = MaterialTheme.typography.bodySmall)
            }

            // Success
            successMessage?.let {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                Text(it, color = DesignTokens.Colors.Success, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            Button(
                onClick = {
                    // Validate
                    when {
                        oldPassword.isBlank() -> {
                            errorMessage = context.getString(R.string.change_password_error_current_blank)
                            return@Button
                        }
                        else -> {
                            // Use strong password validation
                            val validation = PasswordValidator.validate(newPassword)
                            if (!validation.isValid) {
                                errorMessage = validation.errorMessage
                                return@Button
                            }
                        }
                    }
                    if (newPassword != confirmPassword) {
                        errorMessage = context.getString(R.string.change_password_error_mismatch)
                        return@Button
                    }
                    if (newPassword == oldPassword) {
                        errorMessage = context.getString(R.string.change_password_error_same)
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            FirebaseService.changePassword(oldPassword, newPassword)
                            // Clear the mustChangePassword flag
                            FirebaseService.currentUID?.let { uid ->
                                try {
                                    FirebaseFirestore.getInstance()
                                        .collection("users").document(uid)
                                        .update("mustChangePassword", false)
                                        .await()
                                } catch (_: Exception) { }
                            }
                            successMessage = context.getString(R.string.change_password_success)
                            isLoading = false
                            // Navigate back after short delay
                            kotlinx.coroutines.delay(1500)
                            onSuccess()
                        } catch (e: Exception) {
                            val msg = when {
                                e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ||
                                e.message?.contains("wrong-password") == true ->
                                    context.getString(R.string.change_password_error_current_wrong)
                                else -> ErrorHandler.getDisplayMessage(e, "change password")
                            }
                            errorMessage = msg
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary),
                enabled = !isLoading && oldPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.action_change_password), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
        }
    }
}
