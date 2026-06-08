// LoginScreen.kt — Auth screen composable
package com.srcardiocare.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: (role: String) -> Unit, onChangePassword: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // First login password change dialog state
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf("") }

    // Password change dialog
    if (showPasswordChangeDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = { Text("This is your first login. Would you like to change your password now for better security?") },
            confirmButton = {
                TextButton(onClick = {
                    showPasswordChangeDialog = false
                    // Navigate to change password
                    onChangePassword()
                }) {
                    Text("Change Password", color = DesignTokens.Colors.Primary)
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
                    Text("Skip")
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
                        contentDescription = "SrCardioCare logo",
                        modifier = Modifier
                            .padding(12.dp)
                            .size(82.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                Text(
                    text = "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))

                Text(
                    text = "Sign in to continue your cardiac care journey",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentType = ContentType.EmailAddress },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesignTokens.Colors.Primary,
                        cursorColor = DesignTokens.Colors.Primary
                    )
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesignTokens.Colors.Primary,
                        cursorColor = DesignTokens.Colors.Primary
                    )
                )

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                    Text(text = it, color = DesignTokens.Colors.Error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

                Button(
                    onClick = {
                        val trimmedEmail = email.trim()
                        if (trimmedEmail.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter your email and password."
                            return@Button
                        }
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                            errorMessage = "Please enter a valid email address."
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val result = runCatching { FirebaseService.login(email.trim(), password) }
                            result.onSuccess { userData ->
                                val role = (userData["role"] as? String)?.uppercase() ?: "PATIENT"
                                val mustChangePassword = userData["mustChangePassword"] as? Boolean ?: false
                                val authManager = AuthManager(context)
                                authManager.userRole = role

                                // Register FCM token so backend can send push notifications to this device
                                FirebaseService.currentUID?.let { PushMessagingService.saveFcmToken(it) }

                                // Check if user needs to change password
                                if (mustChangePassword) {
                                    pendingRole = role
                                    isLoading = false
                                    showPasswordChangeDialog = true
                                } else {
                                    onLoginSuccess(role)
                                }
                            }.onFailure { e ->
                                errorMessage = ErrorHandler.getDisplayMessage(e as Exception, "login")
                                isLoading = false
                            }
                        }
                    },
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
                        Text("Sign In", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            Text(
                text = "Powered by SRET-AIDA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = DesignTokens.Spacing.MD)
            )
        }
    }
}
