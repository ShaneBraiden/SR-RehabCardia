// DoctorProfileScreen.kt — Doctor / Admin profile (editable, profile-only)
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
fun DoctorProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onChangePassword: () -> Unit = {},
    viewModel: DoctorProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast = rememberToast()

    val ui by viewModel.state.collectAsStateWithLifecycle()

    var isEditing by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    var editFirstName by remember { mutableStateOf("") }
    var editLastName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editLicenseNumber by remember { mutableStateOf("") }

    LogoutConfirmDialog(
        show = showLogoutDialog,
        onDismiss = { showLogoutDialog = false },
        onConfirm = {
            FirebaseService.logout()
            AuthManager(context).clearAll()
            onLogout()
        }
    )

    fun beginEdit() {
        editFirstName = ui.firstName
        editLastName = ui.lastName
        editPhone = ui.phone
        editLicenseNumber = ui.licenseNumber
        isEditing = true
    }

    fun saveEdits() {
        viewModel.save(
            editFirstName = editFirstName,
            editLastName = editLastName,
            editPhone = editPhone,
            editLicenseNumber = editLicenseNumber,
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
                title = { Text("Profile", fontWeight = FontWeight.SemiBold) },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        if (ui.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DesignTokens.Spacing.XL)
            ) {
                ProfileFormSkeleton()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = DesignTokens.Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            val initials = "${ui.firstName.firstOrNull() ?: ""}${ui.lastName.firstOrNull() ?: ""}".uppercase()
            InitialsAvatar(initials = initials.ifBlank { "?" }, size = 88.dp)

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            Text(
                text = if (ui.role.equals("Doctor", ignoreCase = true)) "Dr. ${ui.firstName} ${ui.lastName}" else "${ui.firstName} ${ui.lastName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                    .background(DesignTokens.Colors.PrimaryLight)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    ui.role,
                    style = MaterialTheme.typography.labelMedium,
                    color = DesignTokens.Colors.PrimaryDark,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(DesignTokens.Spacing.XL)) {
                    Text("Account Information", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

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
                        if (ui.role.equals("Doctor", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            OutlinedTextField(
                                value = editLicenseNumber,
                                onValueChange = { editLicenseNumber = InputValidator.limitLength(it, InputValidator.MaxLength.LICENSE_NUMBER) },
                                label = { Text("License Number") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                        ProfileInfoRow(label = "Email (not editable)", value = ui.email, showDivider = false)
                    } else {
                        ProfileInfoRow(label = "First Name", value = ui.firstName)
                        ProfileInfoRow(label = "Last Name", value = ui.lastName)
                        ProfileInfoRow(label = "Email", value = ui.email)
                        ProfileInfoRow(label = "Phone", value = ui.phone.ifBlank { "—" })
                        ProfileInfoRow(label = "Role", value = ui.role, showDivider = ui.role.equals("Doctor", ignoreCase = true) && ui.licenseNumber.isNotBlank())
                        if (ui.role.equals("Doctor", ignoreCase = true) && ui.licenseNumber.isNotBlank()) {
                            ProfileInfoRow(label = "License Number", value = ui.licenseNumber, showDivider = false)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
                ) {
                    OutlinedButton(
                        onClick = { isEditing = false },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(DesignTokens.Radius.Button),
                        enabled = !ui.isSaving
                    ) { Text("Cancel") }

                    Button(
                        onClick = { saveEdits() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(DesignTokens.Radius.Button),
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Button),
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary)
            ) {
                Text("Change Password", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Button),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DesignTokens.Colors.Error),
                border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Colors.Error)
            ) {
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
        }
    }
}
