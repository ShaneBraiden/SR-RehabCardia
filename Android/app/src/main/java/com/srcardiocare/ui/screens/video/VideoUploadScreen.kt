// VideoUploadScreen.kt — Upload exercise video and save metadata to Firestore
package com.srcardiocare.ui.screens.video

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoUploadScreen(onBack: () -> Unit, onUploaded: () -> Unit) {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var exerciseName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var isUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast = rememberToast()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideoUri = it
            selectedFileName = it.lastPathSegment ?: "video.mp4"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Exercise Video", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.XL)
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // Video picker zone
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(DesignTokens.Radius.LG),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                onClick = { videoPicker.launch("video/*") }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 2.dp,
                            color = DesignTokens.Colors.NeutralLight,
                            shape = RoundedCornerShape(DesignTokens.Radius.LG)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (selectedVideoUri != null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(48.dp), tint = DesignTokens.Colors.Success)
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            Text("Video selected", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(selectedFileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            Text("Tap to select video", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text("MP4, MOV — Max 500 MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (isUploading) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = DesignTokens.Colors.Primary,
                    trackColor = DesignTokens.Colors.NeutralLight,
                )
                Text(
                    "${(uploadProgress * 100).toInt()}%",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            OutlinedTextField(
                value = exerciseName,
                onValueChange = { exerciseName = InputValidator.limitLength(it, InputValidator.MaxLength.EXERCISE_NAME) },
                label = { Text("Exercise Name") }, placeholder = { Text("e.g. Knee Flexion") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            OutlinedTextField(
                value = category,
                onValueChange = { category = InputValidator.limitLength(it, InputValidator.MaxLength.CATEGORY) },
                label = { Text("Category") }, placeholder = { Text("e.g. Knee") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            OutlinedTextField(
                value = difficulty,
                onValueChange = { difficulty = InputValidator.limitLength(it, InputValidator.MaxLength.CATEGORY) },
                label = { Text("Difficulty") }, placeholder = { Text("Beginner / Intermediate / Advanced") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            OutlinedTextField(
                value = duration,
                onValueChange = { newVal ->
                    // Only allow digits
                    if (newVal.all { it.isDigit() } && newVal.length <= 6) duration = newVal
                },
                label = { Text("Duration (seconds)") }, placeholder = { Text("e.g. 120") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = InputValidator.limitLength(it, InputValidator.MaxLength.INSTRUCTIONS) },
                label = { Text("Instructions") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DesignTokens.Colors.Primary)
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))

            Button(
                onClick = {
                    isUploading = true
                    uploadProgress = 0.1f

                    scope.launch {
                        try {
                            // 1. Read video bytes
                            val bytes = selectedVideoUri?.let { uri ->
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            } ?: throw Exception("Could not read video file")
                            
                            uploadProgress = 0.4f

                            // 2. Upload video to Firebase Storage
                            val mimeType = context.contentResolver.getType(selectedVideoUri!!) ?: "video/mp4"
                            val downloadUrl = FirebaseService.uploadVideo(bytes, mimeType)
                            
                            uploadProgress = 0.8f

                            // 3. Save exercise metadata to Firestore
                            val exerciseData = mutableMapOf<String, Any>(
                                "name" to exerciseName.trim(),
                                "title" to exerciseName.trim(),
                                "category" to category.trim(),
                                "difficulty" to difficulty.trim(),
                                "instructions" to instructions.trim(),
                                "uploadedBy" to (FirebaseService.currentUID ?: "unknown"),
                                "videoUrl" to downloadUrl,
                                "videoFileName" to selectedFileName
                            )
                            if (duration.isNotBlank()) {
                                val durationInt = duration.trim().toIntOrNull()
                                if (durationInt != null && durationInt > 0) {
                                    exerciseData["duration"] = durationInt
                                }
                            }

                            FirebaseService.createExercise(exerciseData)
                            uploadProgress = 1.0f

                            // Success — navigate back
                            toast("Exercise added")
                            onUploaded()
                        } catch (e: Exception) {
                            isUploading = false
                            uploadProgress = 0f
                            toast("Failed to upload exercise")
                            errorMessage = ErrorHandler.getDisplayMessage(e, "upload exercise")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Primary),
                enabled = !isUploading && exerciseName.isNotBlank() && selectedVideoUri != null
            ) {
                Text(
                    if (isUploading) "Saving Exercise…" else "Upload & Save Exercise",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.XXL))
        }
    }
}
