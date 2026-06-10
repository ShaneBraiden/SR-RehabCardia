// AdminDoctorPatientsScreen.kt — Shows patients for a specific doctor (Admin view)
package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

data class DoctorPatientItem(
    val id: String,
    val name: String,
    val email: String,
    val initials: String,
    val isOnline: Boolean,
    val assignmentCount: Int,
    val completionRate: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDoctorPatientsScreen(
    doctorId: String,
    onPatientTap: (String) -> Unit,
    onBack: () -> Unit
) {
    var doctorName by remember { mutableStateOf("Loading...") }
    var patients by remember { mutableStateOf<List<DoctorPatientItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    suspend fun loadData() {
        try {
            // Fetch doctor info
            val doctor = UserRepository.getUser(doctorId)
            doctorName = "Dr. ${doctor.fullName}".trim()

            // Fetch doctor's patients
            val patientList = UserRepository.getPatients(doctorId)
            
            patients = patientList.mapNotNull { patient ->
                val email = patient.email
                val name = patient.fullName.ifBlank { email }
                val initials = "${patient.firstName.firstOrNull() ?: ""}${patient.lastName.firstOrNull() ?: ""}".uppercase().ifBlank { "?" }

                // Check online status
                val isOnline = patient.isOnline

                // Get assignment count and completion rate
                val assignments = try {
                    FirebaseService.fetchAssignments(patient.id)
                } catch (_: Exception) { emptyList() }
                
                val completedAssignments = assignments.count { (_, assignData) ->
                    val status = assignData["status"] as? String
                    status == "COMPLETED"
                }
                val completionRate = if (assignments.isNotEmpty()) {
                    completedAssignments.toFloat() / assignments.size
                } else 0f

                DoctorPatientItem(
                    id = patient.id,
                    name = name,
                    email = email,
                    initials = initials,
                    isOnline = isOnline,
                    assignmentCount = assignments.size,
                    completionRate = completionRate
                )
            }
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = ErrorHandler.getDisplayMessage(e, "load patients")
        }
        isLoading = false
        isRefreshing = false
    }

    LaunchedEffect(doctorId) { loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doctorName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Patients", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch { loadData() }
            },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            when {
                isLoading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = DesignTokens.Spacing.MD)
                    ) {
                        items(6) { SkeletonListRow() }
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.XL), contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Error.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.padding(DesignTokens.Spacing.XL),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = DesignTokens.Colors.Error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                                Text("Error", fontWeight = FontWeight.Bold, color = DesignTokens.Colors.Error)
                                Text(errorMessage ?: "", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                patients.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.XL), contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(DesignTokens.Radius.Card),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(DesignTokens.Spacing.XXXL),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                                Text("No patients assigned", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "This doctor has no patients yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = DesignTokens.Spacing.MD)
                    ) {
                        // Stats header
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                                shape = RoundedCornerShape(DesignTokens.Radius.Card),
                                colors = CardDefaults.cardColors(containerColor = DesignTokens.Colors.Primary)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(DesignTokens.Spacing.LG),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            patients.size.toString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text("Patients", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            patients.count { it.isOnline }.toString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text("Online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val avgCompletion = if (patients.isNotEmpty()) {
                                            (patients.sumOf { it.completionRate.toDouble() } / patients.size * 100).toInt()
                                        } else 0
                                        Text(
                                            "$avgCompletion%",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text("Avg Completion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                        }

                        items(patients) { patient ->
                            DoctorPatientCard(patient = patient, onClick = { onPatientTap(patient.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoctorPatientCard(patient: DoctorPatientItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.XS)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(DesignTokens.Spacing.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DesignTokens.Colors.Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        patient.initials,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = DesignTokens.Colors.Primary
                    )
                }
                if (patient.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(DesignTokens.Colors.Success)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))

            // Patient info
            Column(modifier = Modifier.weight(1f)) {
                Text(patient.name, fontWeight = FontWeight.SemiBold)
                Text(patient.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = DesignTokens.Colors.Primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${patient.assignmentCount} exercises",
                        style = MaterialTheme.typography.labelSmall,
                        color = DesignTokens.Colors.Primary
                    )
                }
            }

            // Completion indicator
            val statusColor = when {
                patient.completionRate >= 1f -> DesignTokens.Colors.Success
                patient.completionRate >= 0.5f -> DesignTokens.Colors.Warning
                else -> DesignTokens.Colors.Error
            }
            val statusIcon = when {
                patient.completionRate >= 1f -> Icons.Default.CheckCircle
                patient.completionRate >= 0.5f -> Icons.Default.Warning
                else -> Icons.Default.Cancel
            }
            Column(horizontalAlignment = Alignment.End) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(24.dp))
                Text(
                    "${(patient.completionRate * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }
    }
}
