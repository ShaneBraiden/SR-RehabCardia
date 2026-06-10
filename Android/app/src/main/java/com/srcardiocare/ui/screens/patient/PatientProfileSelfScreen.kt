// PatientProfileSelfScreen.kt — Patient's own profile screen
package com.srcardiocare.ui.screens.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srcardiocare.core.auth.AuthManager
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.InitialsAvatar
import com.srcardiocare.ui.components.LogoutConfirmDialog
import com.srcardiocare.ui.components.ProfileFormSkeleton
import com.srcardiocare.ui.components.ProfileInfoRow
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientProfileSelfScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onChangePassword: () -> Unit = {},
    viewModel: PatientProfileSelfViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast = rememberToast()

    val ui by viewModel.state.collectAsStateWithLifecycle()

    var isEditing by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Editable fields (staging buffer)
    var editFirstName by remember { mutableStateOf("") }
    var editLastName by remember { mutableStateOf("") }
    var editCondition by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }

    LogoutConfirmDialog(
        show = showLogoutDialog,
        onDismiss = { showLogoutDialog = false },
        onConfirm = {
            FirebaseService.logout()
            AuthManager(context).clearAll()
            onLogout()
        }
    )

    val initials = "${ui.firstName.firstOrNull() ?: ""}${ui.lastName.firstOrNull() ?: ""}".uppercase()

    fun beginEdit() {
        editFirstName = ui.firstName
        editLastName = ui.lastName
        editCondition = ui.condition
        editPhone = ui.phone
        isEditing = true
    }

    fun saveEdits() {
        viewModel.save(
            editFirstName = editFirstName,
            editLastName = editLastName,
            editPhone = editPhone,
            editCondition = editCondition,
            onValidationError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            onSuccess = {
                isEditing = false
                toast("Profile updated")
            },
            onError = { msg ->
                toast("Failed to update profile")
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isEditing && !ui.isLoading) {
                        IconButton(onClick = { beginEdit() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit profile")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (ui.isLoading) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DesignTokens.Spacing.XL)
            ) {
                ProfileFormSkeleton()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXXL))

                InitialsAvatar(initials = initials.ifBlank { "?" }, size = 96.dp)

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                Text(
                    "${ui.firstName} ${ui.lastName}".trim().ifBlank { "Patient" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(ui.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL),
                    shape = RoundedCornerShape(DesignTokens.Radius.Card),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                        if (isEditing) {
                            OutlinedTextField(
                                value = editFirstName,
                                onValueChange = { editFirstName = InputValidator.limitLength(it, InputValidator.MaxLength.NAME) },
                                label = { Text("First Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            OutlinedTextField(
                                value = editLastName,
                                onValueChange = { editLastName = InputValidator.limitLength(it, InputValidator.MaxLength.NAME) },
                                label = { Text("Last Name (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            OutlinedTextField(
                                value = editPhone,
                                onValueChange = { editPhone = InputValidator.limitLength(it, InputValidator.MaxLength.PHONE) },
                                label = { Text("Phone") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            OutlinedTextField(
                                value = editCondition,
                                onValueChange = { editCondition = InputValidator.limitLength(it, InputValidator.MaxLength.INJURY_TYPE) },
                                label = { Text("Condition") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            ProfileInfoRow(label = "Email (not editable)", value = ui.email, showDivider = false)
                            ProfileInfoRow(label = "Assigned Doctor", value = ui.assignedDoctor, showDivider = false)
                        } else {
                            ProfileInfoRow(label = "First Name", value = ui.firstName)
                            ProfileInfoRow(label = "Last Name", value = ui.lastName)
                            ProfileInfoRow(label = "Phone", value = ui.phone.ifBlank { "—" })
                            ProfileInfoRow(label = "Condition", value = ui.condition.ifBlank { "None listed" })
                            ProfileInfoRow(label = "Assigned Doctor", value = ui.assignedDoctor)
                            ProfileInfoRow(label = "Email", value = ui.email, showDivider = false)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

                if (isEditing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.XL),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                    ) {
                        OutlinedButton(
                            onClick = { isEditing = false },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(DesignTokens.Radius.Base),
                            enabled = !ui.isSaving
                        ) { Text("Cancel") }

                        Button(
                            onClick = { saveEdits() },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(DesignTokens.Radius.Base),
                            colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary),
                            enabled = !ui.isSaving
                        ) {
                            if (ui.isSaving) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                }

                Button(
                    onClick = onChangePassword,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                        .height(52.dp),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary)
                ) {
                    Text("Change Password", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                        .height(52.dp),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignTokens.Colors.Error.copy(alpha = 0.1f),
                        contentColor = DesignTokens.Colors.Error
                    )
                ) {
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXXL))
            }
        }
    }
}
