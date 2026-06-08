package com.srcardiocare.ui.screens.patient

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.ChatRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.data.model.ChatMessage
import com.srcardiocare.ui.theme.DesignTokens
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatientChatScreen(onBack: () -> Unit) {
    var rawMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(rawMessages.size, isImeVisible) {
        if (rawMessages.isNotEmpty()) {
            listState.animateScrollToItem(rawMessages.size)
        }
    }
    
    var currentUid by remember { mutableStateOf("") }
    var currentName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val uid = FirebaseService.currentUID ?: return@LaunchedEffect
        currentUid = uid
        try {
            currentName = UserRepository.getUser(uid).fullName
        } catch (_: Exception) {}

        try {
            FirebaseService.markNotificationsReadByType(uid, "message")
        } catch (_: Exception) { }

        // The chat room ID is just the patient ID
        ChatRepository.observeChatMessagesTyped(currentUid).collect { msgs ->
            rawMessages = msgs
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat with Doctor", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing.MD),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp).padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM)) }
            }

            // Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(DesignTokens.Spacing.SM),
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
                Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                FloatingActionButton(
                    onClick = {
                        val txt = inputText.trim()
                        if (txt.isNotEmpty() && currentUid.isNotEmpty()) {
                            inputText = ""
                            scope.launch {
                                try {
                                    // The chat room ID is currentUid (the patient)
                                    FirebaseService.sendChatMessage(currentUid, currentUid, currentName, txt)
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
}
