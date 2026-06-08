// FeedbackDashboardScreen.kt — Replaced with a patient list for Chat & Feedbacks
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
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

private data class ChatPatientItem(
    val id: String,
    val name: String,
    val initials: String,
    val lastMessageAtMs: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackDashboardScreen(
    onPatientTap: (String) -> Unit,
    onBack: () -> Unit
) {
    var patients by remember { mutableStateOf<List<ChatPatientItem>>(emptyList()) }
    var userRole by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val uid = FirebaseService.currentUID ?: return@LaunchedEffect
            userRole = UserRepository.getUser(uid).role

            val fetched = if (userRole == "admin") {
                UserRepository.getAllPatients()
            } else {
                UserRepository.getPatients(uid)
            }
            val db = FirebaseFirestore.getInstance()
            patients = coroutineScope {
                fetched.map { patient ->
                    async {
                        val initials = "${patient.firstName.firstOrNull() ?: ""}${patient.lastName.firstOrNull() ?: ""}".uppercase()
                        val lastMs = try {
                            val snap = db.collection("chats").document(patient.id).collection("messages")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .limit(1)
                                .get().await()
                            val ts = snap.documents.firstOrNull()?.getTimestamp("timestamp")
                            ts?.toDate()?.time ?: 0L
                        } catch (_: Exception) { 0L }
                        ChatPatientItem(patient.id, patient.fullName, initials.ifBlank { "?" }, lastMs)
                    }
                }.awaitAll()
            }.sortedByDescending { it.lastMessageAtMs }
        } catch (e: Exception) {
            errorMessage = ErrorHandler.getDisplayMessage(e, "load patients")
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (userRole == "admin") "All Patient Chats" else "Select Patient",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = DesignTokens.Spacing.MD)
            ) {
                items(6) { SkeletonListRow() }
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(errorMessage!!, color = DesignTokens.Colors.Error)
            }
        } else if (patients.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (userRole == "admin") "No patient chats available" else "No patients assigned",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = DesignTokens.Spacing.XL),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM)
            ) {
                item { Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM)) }
                items(patients) { patient ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPatientTap(patient.id) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(DesignTokens.Spacing.LG),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(DesignTokens.Colors.PrimaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(patient.initials, fontWeight = FontWeight.Bold, color = DesignTokens.Colors.PrimaryDark)
                            }
                            Spacer(modifier = Modifier.width(DesignTokens.Spacing.MD))
                            Column {
                                Text(patient.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("View Feedbacks & Chat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL)) }
            }
        }
    }
}
