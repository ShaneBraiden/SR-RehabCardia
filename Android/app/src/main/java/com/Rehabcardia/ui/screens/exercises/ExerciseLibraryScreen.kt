// ExerciseLibraryScreen.kt — Searchable exercise grid with category chips and video playback
package com.srcardiocare.ui.screens.exercises

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.data.firebase.ExerciseRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.ShimmerBox
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

private data class ExLibItem(
    val id: String,
    val name: String, 
    val category: String, 
    val difficulty: String, 
    val duration: String,
    val uploadedBy: String,
    val videoUrl: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(onBack: () -> Unit, onUpload: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var allExercises by remember { mutableStateOf<List<ExLibItem>>(emptyList()) }
    var categories by remember { mutableStateOf(listOf("All")) }
    var isLoading by remember { mutableStateOf(true) }

    var currentUserId by remember { mutableStateOf("") }
    var currentUserRole by remember { mutableStateOf("") }
    var showDeleteDialogFor by remember { mutableStateOf<ExLibItem?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var playingVideo by remember { mutableStateOf<ExLibItem?>(null) }
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    LaunchedEffect(Unit) {
        try {
            val uid = FirebaseService.currentUID
            if (uid != null) {
                currentUserId = uid
                currentUserRole = UserRepository.getUser(uid).role
            }

            val rawExercises = ExerciseRepository.getExercises()
            allExercises = rawExercises.map { exercise ->
                val difficulty = exercise.difficultyLevel.replaceFirstChar { it.uppercase() }
                val durationSec = exercise.durationSeconds
                val mins = durationSec / 60
                val secs = durationSec % 60
                val duration = if (secs > 0) "$mins:${secs.toString().padStart(2, '0')}" else "$mins:00"
                ExLibItem(exercise.id, exercise.name, exercise.category, difficulty, duration, exercise.uploadedBy, exercise.videoUrl)
            }
            val cats = allExercises.map { it.category }.distinct().sorted()
            categories = listOf("All") + cats
        } catch (_: Exception) { }
        isLoading = false
    }

    val filtered = allExercises.filter { ex ->
        val matchesCat = selectedCategory == "All" || ex.category == selectedCategory
        val matchesSearch = searchQuery.isBlank() || ex.name.contains(searchQuery, ignoreCase = true)
        matchesCat && matchesSearch
    }

    // Video player dialog
    playingVideo?.let { exercise ->
        VideoPlayerDialog(
            exerciseName = exercise.name,
            videoUrl = exercise.videoUrl,
            onDismiss = { playingVideo = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise Library", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (currentUserRole == "admin" || currentUserRole == "doctor") {
                FloatingActionButton(
                    onClick = onUpload,
                    containerColor = DesignTokens.Colors.Primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Upload Video")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (showDeleteDialogFor != null) {
            val ex = showDeleteDialogFor!!
            AlertDialog(
                onDismissRequest = { if (!isDeleting) showDeleteDialogFor = null },
                title = { Text("Delete Exercise", color = DesignTokens.Colors.Error) },
                text = { Text("Are you sure you want to delete '${ex.name}'? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isDeleting = true
                            scope.launch {
                                try {
                                    FirebaseService.deleteExercise(ex.id, ex.videoUrl)
                                    allExercises = allExercises.filter { it.id != ex.id }
                                    toast("Exercise removed")
                                    showDeleteDialogFor = null
                                } catch (e: Exception) {
                                    toast("Failed to remove exercise")
                                    showDeleteDialogFor = null
                                } finally {
                                    isDeleting = false
                                }
                            }
                        },
                        enabled = !isDeleting
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DesignTokens.Colors.Error, strokeWidth = 2.dp)
                        } else {
                            Text("Delete", color = DesignTokens.Colors.Error, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialogFor = null }, enabled = !isDeleting) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = InputValidator.limitLength(it, InputValidator.MaxLength.TEXT_FIELD) },
                placeholder = { Text("Search exercises…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing.XL),
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DesignTokens.Colors.Primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // Category chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM),
                contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.XL)
            ) {
                items(categories) { cat ->
                    val selected = cat == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DesignTokens.Colors.Primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(DesignTokens.Radius.Full)
                    )
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))

            // Grid (skeleton when loading)
            if (isLoading) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD),
                    contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(6) {
                        Card(
                            shape = RoundedCornerShape(DesignTokens.Radius.LG),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp),
                                    shape = RoundedCornerShape(DesignTokens.Radius.Base)
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(14.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth(0.4f)
                                        .height(10.dp)
                                )
                            }
                        }
                    }
                }
            } else {
            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.MD),
                contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { ex ->
                    Card(
                        shape = RoundedCornerShape(DesignTokens.Radius.LG),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable {
                            // Admin/Doctor can play videos
                            if ((currentUserRole == "admin" || currentUserRole == "doctor") && !ex.videoUrl.isNullOrBlank()) {
                                playingVideo = ex
                            }
                        }
                    ) {
                        Column(modifier = Modifier.padding(DesignTokens.Spacing.MD)) {
                            // Thumbnail placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(DesignTokens.Radius.Base))
                                    .background(DesignTokens.Colors.PrimaryLight.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Play icon for playable videos
                                if ((currentUserRole == "admin" || currentUserRole == "doctor") && !ex.videoUrl.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.Movie, contentDescription = "Video", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                // Delete button for authorized users
                                val canDelete = currentUserRole == "admin" || (currentUserRole == "doctor" && ex.uploadedBy == currentUserId)
                                if (canDelete) {
                                    IconButton(
                                        onClick = { showDeleteDialogFor = ex },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DesignTokens.Colors.Error)
                                    }
                                }

                                // Duration badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(ex.duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                            Text(ex.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                            Text(ex.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            // Difficulty badge
                            val badgeColor = when (ex.difficulty) {
                                "Beginner" -> DesignTokens.Colors.Success
                                "Intermediate" -> DesignTokens.Colors.Warning
                                else -> DesignTokens.Colors.Error
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(DesignTokens.Radius.Full))
                                    .background(badgeColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(ex.difficulty, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            } // end else (isLoading)
        }
    }
}

/**
 * Fullscreen dialog for video playback in Exercise Library.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VideoPlayerDialog(
    exerciseName: String,
    videoUrl: String?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val isYoutube = remember(videoUrl) { !extractYoutubeVideoIdLib(videoUrl.orEmpty()).isNullOrBlank() }

                if (videoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No video available", color = Color.White)
                    }
                } else if (isYoutube) {
                    val videoId = remember(videoUrl) { extractYoutubeVideoIdLib(videoUrl) }
                    // Validate video ID to prevent XSS
                    if (videoId == null || !InputValidator.validateYouTubeVideoId(videoId)) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Invalid video URL", color = Color.White)
                        }
                    } else {
                        val html = """
                            <html><body style="margin:0;padding:0;background:#000;">
                              <iframe width="100%" height="100%"
                                src="https://www.youtube.com/embed/$videoId?playsinline=1&rel=0&autoplay=1"
                                frameborder="0" allowfullscreen
                                allow="autoplay; encrypted-media; picture-in-picture">
                              </iframe>
                            </body></html>
                        """.trimIndent()
                        AndroidView(
                            factory = { context: Context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    webViewClient = WebViewClient()
                                    loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .align(Alignment.Center)
                        )
                    }
                } else {
                    // ExoPlayer for direct video URLs
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val exoPlayer = remember(videoUrl) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(videoUrl))
                            prepare()
                            playWhenReady = true
                        }
                    }

                    DisposableEffect(exoPlayer) {
                        onDispose { exoPlayer.release() }
                    }

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                player = exoPlayer
                            }
                        },
                        update = { it.player = exoPlayer },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .align(Alignment.Center)
                    )
                }

                // Close & title overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Text(
                            exerciseName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

private fun extractYoutubeVideoIdLib(url: String): String? {
    return runCatching {
        val uri = Uri.parse(url)
        when {
            uri.host?.contains("youtu.be") == true -> uri.lastPathSegment
            uri.host?.contains("youtube.com") == true && uri.path?.startsWith("/embed/") == true -> {
                uri.pathSegments.getOrNull(1)
            }
            uri.host?.contains("youtube.com") == true -> uri.getQueryParameter("v")
            else -> null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
