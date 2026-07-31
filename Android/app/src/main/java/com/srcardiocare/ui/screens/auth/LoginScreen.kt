// LoginScreen.kt — Auth screen composable
package com.srcardiocare.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.srcardiocare.R
import com.srcardiocare.core.push.PushMessagingService
import com.srcardiocare.core.auth.AuthManager
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.LanguageToggle
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: (role: String) -> Unit, onChangePassword: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Which field(s) to outline in red. A rejected credential is not attributable
    // to one field or the other, so both are marked and the message carries the
    // detail — see error_invalid_credentials.
    var emailInvalid by remember { mutableStateOf(false) }
    var passwordInvalid by remember { mutableStateOf(false) }

    fun clearError() {
        errorMessage = null
        emailInvalid = false
        passwordInvalid = false
    }

    // First login password change dialog state
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf("") }

    // Shared by the Sign In button and the keyboard's Done action so both routes
    // validate and report failures identically.
    fun submit() {
        if (isLoading) return
        keyboard?.hide()
        val trimmedEmail = email.trim()

        // Local checks first — no point spending a network round trip, and the
        // user gets told exactly which field needs attention.
        when {
            trimmedEmail.isBlank() && password.isBlank() -> {
                errorMessage = context.getString(R.string.login_error_empty_fields)
                emailInvalid = true
                passwordInvalid = true
                return
            }
            trimmedEmail.isBlank() -> {
                errorMessage = context.getString(R.string.login_error_email_required)
                emailInvalid = true
                passwordInvalid = false
                return
            }
            password.isBlank() -> {
                errorMessage = context.getString(R.string.login_error_password_required)
                emailInvalid = false
                passwordInvalid = true
                return
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches() -> {
                errorMessage = context.getString(R.string.login_error_invalid_email)
                emailInvalid = true
                passwordInvalid = false
                return
            }
        }

        isLoading = true
        clearError()
        scope.launch {
            val result = runCatching { FirebaseService.login(trimmedEmail, password) }
            result.onSuccess { userData ->
                val role = (userData["role"] as? String)?.uppercase() ?: "PATIENT"
                val mustChangePassword = userData["mustChangePassword"] as? Boolean ?: false
                val authManager = AuthManager(context)
                authManager.userRole = role

                // Register FCM token so backend can send push notifications to this device
                FirebaseService.currentUID?.let { PushMessagingService.saveFcmToken(it) }

                isLoading = false
                // Check if user needs to change password
                if (mustChangePassword) {
                    pendingRole = role
                    showPasswordChangeDialog = true
                } else {
                    onLoginSuccess(role)
                }
            }.onFailure { throwable ->
                // runCatching also catches Error, which is not an Exception —
                // casting blindly would replace the real failure with a
                // ClassCastException and show the wrong message.
                val e = throwable as? Exception ?: Exception(throwable)
                errorMessage = ErrorHandler.getDisplayMessage(e, "login")
                // Both fields go red: with email-enumeration protection the
                // server does not say which one was wrong.
                emailInvalid = true
                passwordInvalid = true
                isLoading = false
            }
        }
    }

    // Password change dialog
    if (showPasswordChangeDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.action_change_password), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.login_first_login_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPasswordChangeDialog = false
                    // Navigate to change password
                    onChangePassword()
                }) {
                    Text(stringResource(R.string.action_change_password), color = DesignTokens.Colors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordChangeDialog = false
                    // Clear the flag and continue
                    scope.launch {
                        try {
                            FirebaseService.currentUID?.let { uid ->
                                FirebaseFirestore.getInstance()
                                    .collection("users").document(uid)
                                    .update("mustChangePassword", false)
                                    .await()
                            }
                        } catch (_: Exception) { }
                        onLoginSuccess(pendingRole)
                    }
                }) {
                    Text(stringResource(R.string.action_skip))
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Sits above the form so a Tamil-only patient can switch before
            // having to read anything.
            LanguageToggle(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(DesignTokens.Spacing.MD)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DesignTokens.Spacing.XL)
                    .padding(bottom = 48.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sr_logo),
                        contentDescription = stringResource(R.string.login_logo_desc),
                        modifier = Modifier
                            .padding(12.dp)
                            .size(82.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                Text(
                    text = stringResource(R.string.login_welcome_back),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))

                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))

                OutlinedTextField(
                    value = email,
                    // Typing is the user acting on the correction — drop the
                    // error rather than leaving stale red text under the form.
                    onValueChange = { email = it; clearError() },
                    label = { Text(stringResource(R.string.login_email_label)) },
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentType = ContentType.EmailAddress },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    singleLine = true,
                    isError = emailInvalid,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesignTokens.Colors.Primary,
                        cursorColor = DesignTokens.Colors.Primary
                    )
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; clearError() },
                    label = { Text(stringResource(R.string.login_password_label)) },
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    // A patient who mistyped their password should be able to see
                    // what they typed rather than guess again blind.
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.login_hide_password else R.string.login_show_password
                                )
                            )
                        }
                    },
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    singleLine = true,
                    isError = passwordInvalid,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesignTokens.Colors.Primary,
                        cursorColor = DesignTokens.Colors.Primary
                    )
                )

                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                    // A bare line of small red text under the fields was easy to
                    // miss, which read as "nothing happened" on a failed sign-in.
                    // Give the failure a surface of its own, and announce it to
                    // screen readers as soon as it appears.
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Assertive },
                        shape = RoundedCornerShape(DesignTokens.Radius.Base),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(DesignTokens.Spacing.MD),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = stringResource(R.string.login_error_icon_desc),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

                Button(
                    onClick = { submit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DesignTokens.Spacing.XXL + DesignTokens.Spacing.MD),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(DesignTokens.Spacing.XL),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = DesignTokens.Spacing.XXS
                        )
                    } else {
                        Text(stringResource(R.string.login_sign_in), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
