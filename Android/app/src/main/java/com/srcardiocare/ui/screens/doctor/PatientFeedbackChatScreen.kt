package com.srcardiocare.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.ChatRepository
import com.srcardiocare.data.firebase.FeedbackRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.data.model.ChatMessage
import com.srcardiocare.ui.components.SkeletonListRow
import com.srcardiocare.ui.theme.DesignTokens
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

private data class FeedbackItem(
    val id: String,
    val patientName: String,
    val dateStr: String,
    val stress: Boolean,
    val strain: Boolean,
    val respiratory: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientFeedbackChatScreen(
    patientId: String,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Feedbacks", "Chat")

    var patientName by remember { mutableStateOf("Loading...") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(patientId) {
        try {
            patientName = UserRepository.getUser(patientId).fullName.ifBlank { "Patient" }
        } catch (e: Exception) {
            patientName = "Patient"
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(patientName, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = DesignTokens.Colors.Primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .imePadding()) {
            if (selectedTab == 0) {
                FeedbackTabView(patientId = patientId, patientName = patientName)
            } else {
                ChatTabView(patientId = patientId)
            }
        }
    }
}

@Composable
private fun FeedbackTabView(patientId: String, patientName: String) {
    var feedbacks by remember { mutableStateOf<List<FeedbackItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(patientId) {
        try {
            val res = FeedbackRepository.getPatientFeedbacks(patientId)
            val sdf = SimpleDateFormat("dd/MM/yyyy • hh:mm a", Locale.getDefault())
            feedbacks = res.map { fb ->
                val dateStr = fb.submittedAtMs?.let { sdf.format(java.util.Date(it)) } ?: "Just now"
                FeedbackItem(fb.id, patientName, dateStr, fb.stress, fb.strain, fb.respiratoryDifficulty)
            }
        } catch (e: Exception) {
            // failed
        }
        isLoading = false
    }

    if (isLoading) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = DesignTokens.Spacing.MD)
        ) {
            items(5) { SkeletonListRow() }
        }
    } else if (feedbacks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No feedbacks recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = DesignTokens.Spacing.XL),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD)
        ) {
            item { Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM)) }
            items(feedbacks) { item ->
                FeedbackCard(item)
            }
            item { Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatTabView(patientId: String) {
    var rawMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var currentUid by remember { mutableStateOf("") }
    var currentName by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(rawMessages.size, isImeVisible) {
        if (rawMessages.isNotEmpty()) {
            listState.animateScrollToItem(rawMessages.size)
        }
    }

    LaunchedEffect(patientId) {
        val uid = FirebaseService.currentUID ?: return@LaunchedEffect
        currentUid = uid
        try {
            currentName = UserRepository.getUser(uid).fullName
        } catch (_: Exception) {}

        ChatRepository.observeChatMessagesTyped(patientId).collect { msgs ->
            rawMessages = msgs
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = DesignTokens.Spacing.MD),
            reverseLayout = false
        ) {
            item { Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM)) }
            items(rawMessages) { msg ->
                val senderId = msg.senderId
                val text = msg.text
                val isMe = senderId == currentUid
                
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val timeStr = msg.timestampMs?.let { sdf.format(java.util.Date(it)) } ?: ""

                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 0.dp,
                                bottomEnd = if (isMe) 0.dp else 16.dp
                            ),
                            color = if (isMe) DesignTokens.Colors.Primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                            tonalElevation = if (isMe) 0.dp else 2.dp
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (timeStr.isNotEmpty()) {
                            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp).padding(top = 2.dp))
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM)) }
        }

        // Input 
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = InputValidator.limitLength(it, InputValidator.MaxLength.CHAT_MESSAGE) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    val txt = inputText.trim()
                    if (txt.isNotEmpty() && currentUid.isNotEmpty()) {
                        inputText = ""
                        scope.launch {
                            try {
                                FirebaseService.sendChatMessage(patientId, currentUid, currentName, txt)
                            } catch (_: Exception) {}
                        }
                    }
                },
                containerColor = DesignTokens.Colors.Primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(24.dp))
            }
        }
    }
}

// Reuse FeedbackCard
@Composable
private fun FeedbackCard(item: FeedbackItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.LG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(DesignTokens.Spacing.LG)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DesignTokens.Colors.PrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = item.patientName.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(2).uppercase()
                        Text(initials, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = DesignTokens.Colors.PrimaryDark)
                    }
                    Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                    Column {
                        Text(item.patientName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(item.dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricChip("Stress", if (item.stress) "Yes" else "No", if (item.stress) DesignTokens.Colors.Error else DesignTokens.Colors.Success)
                MetricChip("Strain", if (item.strain) "Yes" else "No", if (item.strain) DesignTokens.Colors.Error else DesignTokens.Colors.Success)
                
                val respColor = when (item.respiratory) {
                    in 1..3 -> DesignTokens.Colors.Success
                    in 4..6 -> DesignTokens.Colors.Warning
                    else -> DesignTokens.Colors.Error
                }
                MetricChip("Respiratory", "${item.respiratory}/10", respColor)
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(DesignTokens.Radius.SM))
                .background(color.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
