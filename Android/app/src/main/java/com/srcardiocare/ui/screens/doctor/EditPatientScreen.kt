// EditPatientScreen.kt — Doctor/admin form to correct a patient's profile details.
//
// Email is shown but not editable: it is the patient's sign-in identity, which
// lives in Firebase Auth and can only be changed through the Admin SDK.
package com.srcardiocare.ui.screens.doctor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srcardiocare.ui.components.ProfileFormSkeleton
import com.srcardiocare.ui.components.primaryTextFieldColors
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
fun EditPatientScreen(
    patientId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditPatientViewModel = viewModel()
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val f = ui.fields

    var showDiscardDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast = rememberToast()

    fun showError(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    // Leaving with unsaved edits asks first — the form is long enough that a
    // stray back gesture would otherwise throw away several fields of typing.
    fun requestBack() {
        if (ui.hasChanges && !ui.isSaving) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = ui.hasChanges && !ui.isSaving) { showDiscardDialog = true }

    LaunchedEffect(patientId) { viewModel.loadOnce(patientId) }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?", fontWeight = FontWeight.Bold) },
            text = { Text("Your edits to this patient's details have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Discard", color = DesignTokens.Colors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }

    TutorialHost(tourKey = TutorialKeys.EDIT_PATIENT, steps = TutorialTours.editPatient) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Patient", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TutorialHelpButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            ui.isLoading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DesignTokens.Spacing.XL)
            ) {
                ProfileFormSkeleton(rows = 6)
            }

            ui.loadError != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(DesignTokens.Spacing.XL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DesignTokens.Colors.Error, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                Text("Couldn't load this patient", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XS))
                Text(
                    ui.loadError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(DesignTokens.Radius.Base)) { Text("Go back") }
            }

            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DesignTokens.Spacing.XL)
            ) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

                // ── Personal ──────────────────────────────────────────────
                FormSectionLabel("Personal")
                OutlinedTextField(
                    value = f.firstName,
                    onValueChange = viewModel::setFirstName,
                    label = { Text("First Name") },
                    modifier = Modifier.fillMaxWidth().tutorialTarget(TutorialIds.EP_FORM),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    singleLine = true,
                    colors = primaryTextFieldColors()
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                OutlinedTextField(
                    value = f.lastName,
                    onValueChange = viewModel::setLastName,
                    label = { Text("Last Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    singleLine = true,
                    colors = primaryTextFieldColors()
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                OutlinedTextField(
                    value = f.age,
                    onValueChange = viewModel::setAge,
                    label = { Text("Age") },
                    placeholder = { Text("e.g. 32") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = primaryTextFieldColors()
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                Text("Gender", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PATIENT_GENDERS.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = f.genderIndex == index,
                            onClick = { viewModel.setGender(index) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = PATIENT_GENDERS.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = DesignTokens.Colors.Primary.copy(alpha = 0.15f),
                                activeContentColor = DesignTokens.Colors.Primary
                            )
                        ) { Text(label) }
                    }
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

                // ── Contact ───────────────────────────────────────────────
                FormSectionLabel("Contact")
                OutlinedTextField(
                    value = f.phone,
                    onValueChange = viewModel::setPhone,
                    label = { Text("Mobile Number") },
                    placeholder = { Text("e.g. 9876543210") },
                    // The default password was derived from the number at
                    // creation; say plainly that editing it here does not
                    // change the patient's sign-in.
                    supportingText = { Text("10 digits, no country code. Changing it does not change the patient's password.") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    singleLine = true,
                    colors = primaryTextFieldColors()
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                OutlinedTextField(
                    value = ui.email,
                    onValueChange = {},
                    label = { Text("Email") },
                    supportingText = { Text("Used to sign in — can't be changed here") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(DesignTokens.Radius.Base)
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))

                // ── Clinical ──────────────────────────────────────────────
                FormSectionLabel("Clinical")
                OutlinedTextField(
                    value = f.condition,
                    onValueChange = viewModel::setCondition,
                    label = { Text("Condition") },
                    placeholder = { Text("e.g. Hypertension") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    singleLine = true,
                    colors = primaryTextFieldColors()
                )

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

                Button(
                    onClick = {
                        viewModel.save(
                            patientId = patientId,
                            onValidationError = ::showError,
                            onSuccess = {
                                toast("Patient details updated")
                                onSaved()
                            },
                            onError = ::showError
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).tutorialTarget(TutorialIds.EP_SAVE),
                    shape = RoundedCornerShape(DesignTokens.Radius.Base),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary),
                    enabled = ui.hasChanges && !ui.isSaving
                ) {
                    if (ui.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Save Changes", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
            }
        }
    }
    }
}

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = DesignTokens.Spacing.SM)
    )
}
