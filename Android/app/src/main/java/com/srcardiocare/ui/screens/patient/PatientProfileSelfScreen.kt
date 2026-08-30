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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.srcardiocare.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srcardiocare.core.auth.signOutAndRestart
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.ui.components.InitialsAvatar
import com.srcardiocare.ui.components.LegalLinksRow
import com.srcardiocare.ui.components.LogoutConfirmDialog
import com.srcardiocare.ui.components.ProfileFormSkeleton
import com.srcardiocare.ui.components.ProfileInfoRow
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.components.tutorial.TutorialHelpButton
import com.srcardiocare.ui.components.tutorial.TutorialHost
import com.srcardiocare.ui.components.tutorial.TutorialIds
import com.srcardiocare.ui.components.tutorial.TutorialKeys
import com.srcardiocare.ui.components.tutorial.TutorialTours
import com.srcardiocare.ui.components.tutorial.tutorialTarget
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientProfileSelfScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: PatientProfileSelfViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast = rememberToast()

    val ui by viewModel.state.collectAsStateWithLifecycle()

    // "Not assigned" / "Unknown" are UI copy, so they are resolved here rather
    // than baked into the ViewModel, which has no Context and no locale.
    val assignedDoctorLabel = when (val doctor = ui.assignedDoctor) {
        is PatientProfileSelfViewModel.AssignedDoctor.Named -> doctor.name
        PatientProfileSelfViewModel.AssignedDoctor.Unknown ->
            stringResource(R.string.profile_doctor_unknown)
        PatientProfileSelfViewModel.AssignedDoctor.NotAssigned ->
            stringResource(R.string.profile_doctor_not_assigned)
    }

    var isEditing by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Editable fields (staging buffer)
    var editFirstName by remember { mutableStateOf("") }
    var editLastName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }

    LogoutConfirmDialog(
        show = showLogoutDialog,
        onDismiss = { showLogoutDialog = false },
        onConfirm = { signOutAndRestart(context) }
    )

    val initials = "${ui.firstName.firstOrNull() ?: ""}${ui.lastName.firstOrNull() ?: ""}".uppercase()

    fun beginEdit() {
        editFirstName = ui.firstName
        editLastName = ui.lastName
        editPhone = ui.phone
        isEditing = true
    }

    fun saveEdits() {
        viewModel.save(
            editFirstName = editFirstName,
            editLastName = editLastName,
            editPhone = editPhone,
            nameFieldLabel = context.getString(R.string.profile_name_field_label),
            onValidationError = { field, msg ->
                val text = msg ?: context.getString(
                    when (field) {
                        PatientProfileSelfViewModel.ProfileField.NAME -> R.string.profile_invalid_name
                        PatientProfileSelfViewModel.ProfileField.PHONE -> R.string.profile_invalid_phone
                    }
                )
                scope.launch { snackbarHostState.showSnackbar(text) }
            },
            onSuccess = {
                isEditing = false
                toast(context.getString(R.string.profile_updated))
            },
            onError = { msg ->
                toast(context.getString(R.string.profile_update_failed))
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    TutorialHost(tourKey = TutorialKeys.PATIENT_PROFILE, steps = TutorialTours.patientProfile) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!isEditing && !ui.isLoading) {
                        IconButton(
                            onClick = { beginEdit() },
                            modifier = Modifier.tutorialTarget(TutorialIds.PROFILE_EDIT)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.profile_edit))
                        }
                    }
                    TutorialHelpButton()
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
                    "${ui.firstName} ${ui.lastName}".trim()
                        .ifBlank { stringResource(R.string.feedback_patient_fallback) },
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
                                label = { Text(stringResource(R.string.profile_first_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            OutlinedTextField(
                                value = editLastName,
                                onValueChange = { editLastName = InputValidator.limitLength(it, InputValidator.MaxLength.NAME) },
                                label = { Text(stringResource(R.string.profile_last_name_optional)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            OutlinedTextField(
                                value = editPhone,
                                onValueChange = { editPhone = InputValidator.limitLength(it, InputValidator.MaxLength.PHONE) },
                                label = { Text(stringResource(R.string.profile_phone)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            // Condition is set by the doctor and is not patient-editable.
                            ProfileInfoRow(
                                label = stringResource(R.string.profile_condition_readonly),
                                value = ui.condition.ifBlank { stringResource(R.string.profile_none_listed) }
                            )
                            ProfileInfoRow(label = stringResource(R.string.profile_email_readonly), value = ui.email)
                            ProfileInfoRow(
                                label = stringResource(R.string.profile_assigned_doctor),
                                value = assignedDoctorLabel,
                                showDivider = false
                            )
                        } else {
                            ProfileInfoRow(label = stringResource(R.string.profile_first_name), value = ui.firstName)
                            ProfileInfoRow(label = stringResource(R.string.profile_last_name), value = ui.lastName)
                            ProfileInfoRow(
                                label = stringResource(R.string.profile_phone),
                                value = ui.phone.ifBlank { stringResource(R.string.profile_empty_value) }
                            )
                            ProfileInfoRow(
                                label = stringResource(R.string.profile_condition),
                                value = ui.condition.ifBlank { stringResource(R.string.profile_none_listed) }
                            )
                            ProfileInfoRow(label = stringResource(R.string.profile_assigned_doctor), value = assignedDoctorLabel)
                            ProfileInfoRow(label = stringResource(R.string.profile_email), value = ui.email, showDivider = false)
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
                        ) { Text(stringResource(R.string.action_cancel)) }

                        Button(
                            onClick = { saveEdits() },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(DesignTokens.Radius.Base),
                            colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary),
                            enabled = !ui.isSaving
                        ) {
                            if (ui.isSaving) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                }

                // Language now lives in Settings alongside theme and
                // notifications, rather than being the one preference with a
                // home of its own.
                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                        .height(52.dp),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = DesignTokens.Colors.Primary
                    )
                    Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                    Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))

                Button(
                    onClick = onChangePassword,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                        .height(52.dp)
                        .tutorialTarget(TutorialIds.PROFILE_PASSWORD),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary)
                ) {
                    Text(stringResource(R.string.action_change_password), fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.XL)
                        .height(52.dp)
                        .tutorialTarget(TutorialIds.PROFILE_LOGOUT),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignTokens.Colors.Error.copy(alpha = 0.1f),
                        contentColor = DesignTokens.Colors.Error
                    )
                ) {
                    Text(stringResource(R.string.action_sign_out), fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))

                LegalLinksRow()

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXXL))
            }
        }
    }
    }
}
