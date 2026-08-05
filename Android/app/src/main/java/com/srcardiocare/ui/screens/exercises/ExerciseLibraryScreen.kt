// ExerciseLibraryScreen.kt — Searchable exercise grid with category chips and video playback
package com.srcardiocare.ui.screens.exercises

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import android.webkit.WebResourceRequest
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
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.srcardiocare.R
import com.srcardiocare.core.security.InputValidator
import com.srcardiocare.ui.components.FullscreenToggleButton
import com.srcardiocare.ui.components.FullscreenVideoEffect
import com.srcardiocare.data.firebase.ExerciseRepository
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.firebase.UserRepository
import com.srcardiocare.ui.components.ShimmerBox
import com.srcardiocare.ui.components.PlayerLifecycleEffect
import com.srcardiocare.ui.components.VideoLoadingOverlay
import com.srcardiocare.ui.components.WebViewLifecycleEffect
import com.srcardiocare.ui.components.rememberVideoLoadingState
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.components.tutorial.TutorialHelpButton
import com.srcardiocare.ui.components.tutorial.TutorialHost
import com.srcardiocare.ui.components.tutorial.TutorialIds
import com.srcardiocare.ui.components.tutorial.TutorialKeys
import com.srcardiocare.ui.components.tutorial.TutorialTours
import com.srcardiocare.ui.components.tutorial.tutorialTarget
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.launch

private data class ExLibItem(
    val id: String,
    val name: String,
    val category: String,
    val group: String,
    val difficulty: String,
    val duration: String,
    val uploadedBy: String,
    val videoUrl: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(onBack: () -> Unit, onUpload: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    // null = "All". A translated sentinel would break the equality check below,
    // and the real category values are doctor-authored Firestore data that must
    // stay exactly as written.
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    // The second axis, filtered independently of category — a doctor looking
    // for breathing work in month three wants both narrowed at once.
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var allExercises by remember { mutableStateOf<List<ExLibItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var currentUserId by remember { mutableStateOf("") }
    var currentUserRole by remember { mutableStateOf("") }
    var showDeleteDialogFor by remember { mutableStateOf<ExLibItem?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var playingVideo by remember { mutableStateOf<ExLibItem?>(null) }
    val context = LocalContext.current
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
                ExLibItem(exercise.id, exercise.name, exercise.category, exercise.group, difficulty, duration, exercise.uploadedBy, exercise.videoUrl)
            }
            categories = allExercises.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
            groups = allExercises.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
        } catch (_: Exception) { }
        isLoading = false
    }

    val filtered = allExercises.filter { ex ->
        val matchesCat = selectedCategory == null || ex.category == selectedCategory
        val matchesGroup = selectedGroup == null || ex.group == selectedGroup
        val matchesSearch = searchQuery.isBlank() || ex.name.contains(searchQuery, ignoreCase = true)
        matchesCat && matchesGroup && matchesSearch
    }

    // Video player dialog
    playingVideo?.let { exercise ->
        VideoPlayerDialog(
            exerciseName = exercise.name,
            videoUrl = exercise.videoUrl,
            onDismiss = { playingVideo = null }
        )
    }

    TutorialHost(tourKey = TutorialKeys.EXERCISE_LIBRARY, steps = TutorialTours.exerciseLibrary) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercise_library_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = { TutorialHelpButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (currentUserRole == "admin" || currentUserRole == "doctor") {
                FloatingActionButton(
                    onClick = onUpload,
                    containerColor = DesignTokens.Colors.Primary,
                    contentColor = Color.White,
                    modifier = Modifier.tutorialTarget(TutorialIds.LIBRARY_UPLOAD)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.exercise_upload_video))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (showDeleteDialogFor != null) {
            val ex = showDeleteDialogFor!!
            AlertDialog(
                onDismissRequest = { if (!isDeleting) showDeleteDialogFor = null },
                title = { Text(stringResource(R.string.exercise_delete_title), color = DesignTokens.Colors.Error) },
                text = { Text(stringResource(R.string.exercise_delete_message, ex.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isDeleting = true
                            scope.launch {
                                try {
                                    FirebaseService.deleteExercise(ex.id, ex.videoUrl)
                                    allExercises = allExercises.filter { it.id != ex.id }
                                    toast(context.getString(R.string.exercise_removed))
                                    showDeleteDialogFor = null
                                } catch (e: Exception) {
                                    toast(context.getString(R.string.exercise_remove_failed))
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
                            Text(stringResource(R.string.action_delete), color = DesignTokens.Colors.Error, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialogFor = null }, enabled = !isDeleting) {
                        Text(stringResource(R.string.action_cancel))
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
                placeholder = { Text(stringResource(R.string.exercise_library_search)) },
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

            // Category chips — the rehab stage (1st week, 3rd month, …)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM),
                contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.XL)
            ) {
                // null is the "All" chip; the rest are Firestore category values.
                items(listOf<String?>(null) + categories) { cat ->
                    FilterChip(
                        selected = cat == selectedCategory,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat ?: stringResource(R.string.filter_all)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DesignTokens.Colors.Primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(DesignTokens.Radius.Full)
                    )
                }
            }

            // Group chips — the second axis. The row is hidden entirely until
            // at least one exercise carries a group, so a library that never
            // adopts the field looks exactly as it did before.
            if (groups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM),
                    contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.XL)
                ) {
                    items(listOf<String?>(null) + groups) { grp ->
                        FilterChip(
                            selected = grp == selectedGroup,
                            onClick = { selectedGroup = grp },
                            label = { Text(grp ?: stringResource(R.string.filter_all_groups)) },
                            colors = FilterChipDefaults.filterChipColors(
                                // Darker than the stage row's Primary so the two
                                // filters read as different axes, without
                                // dropping the white label below the contrast
                                // the existing chips already hold.
                                selectedContainerColor = DesignTokens.Colors.PrimaryDark,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(DesignTokens.Radius.Full)
                        )
                    }
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
                        modifier = Modifier
                            .then(if (ex.id == filtered.firstOrNull()?.id) Modifier.tutorialTarget(TutorialIds.LIBRARY_ROW) else Modifier)
                            .clickable {
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
                                            contentDescription = stringResource(R.string.action_play),
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.Movie, contentDescription = stringResource(R.string.exercise_video), modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                // Delete button for authorized users
                                val canDelete = currentUserRole == "admin" || (currentUserRole == "doctor" && ex.uploadedBy == currentUserId)
                                if (canDelete) {
                                    IconButton(
                                        onClick = { showDeleteDialogFor = ex },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = DesignTokens.Colors.Error)
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
                            Text(
                                listOf(ex.category, ex.group).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
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

                var isFullscreen by remember { mutableStateOf(false) }
                // Orientation follows the video: detected for direct URLs, 16:9 (landscape) for YouTube.
                var videoRatio by remember { mutableFloatStateOf(16f / 9f) }
                FullscreenVideoEffect(active = isFullscreen, isLandscapeVideo = videoRatio >= 1f)
                BackHandler(enabled = isFullscreen) { isFullscreen = false }

                if (videoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.exercise_no_video), color = Color.White)
                    }
                } else if (isYoutube) {
                    val videoId = remember(videoUrl) { extractYoutubeVideoIdLib(videoUrl) }
                    // Validate video ID to prevent XSS
                    if (videoId == null || !InputValidator.validateYouTubeVideoId(videoId)) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.exercise_invalid_video), color = Color.White)
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
                        // A full-screen black dialog with no spinner is
                        // indistinguishable from a crash on a slow connection.
                        var webLoading by remember(videoId) { mutableStateOf(true) }
                        // Paused when the app is backgrounded; a YouTube iframe
                        // otherwise keeps playing audio behind the launcher.
                        var webViewRef by remember(videoId) { mutableStateOf<WebView?>(null) }
                        WebViewLifecycleEffect(webViewRef)

                        AndroidView(
                            factory = { context: Context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    // Below API 30 these default to true. The player only ever
                                    // needs remote YouTube content, so deny it any path to local
                                    // files or content:// providers.
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.domStorageEnabled = false
                                    // Restrict navigation to YouTube domains only
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            val host = request.url.host ?: return true
                                            val allowed = host.endsWith("youtube.com") || host.endsWith("youtu.be") ||
                                                host.endsWith("youtube-nocookie.com") || host.endsWith("ytimg.com") ||
                                                host.endsWith("googlevideo.com")
                                            return !allowed  // true = block, false = allow
                                        }

                                        override fun onPageFinished(view: WebView, url: String?) {
                                            webLoading = false
                                        }

                                        override fun onReceivedError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            error: android.webkit.WebResourceError
                                        ) {
                                            // Only a main-frame failure means no
                                            // onPageFinished is coming.
                                            if (request.isForMainFrame) webLoading = false
                                        }
                                    }
                                    loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
                                    webViewRef = this
                                }
                            },
                            // The dialog is already full-screen, so give the player the
                            // whole area. Forcing 16:9 made portrait clips (YouTube
                            // Shorts in particular) letterbox down to a small centred
                            // box; the embed scales to fit whatever box it is given, so
                            // a full-screen box maximises the picture at any ratio.
                            modifier = Modifier.fillMaxSize(),
                            // Closing the dialog must stop playback outright, not
                            // leave a detached WebView holding a media session.
                            onRelease = { webView ->
                                webViewRef = null
                                webView.loadUrl("about:blank")
                                webView.destroy()
                            }
                        )
                        VideoLoadingOverlay(
                            visible = webLoading,
                            modifier = Modifier.fillMaxSize()
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
                            addListener(object : Player.Listener {
                                override fun onVideoSizeChanged(videoSize: VideoSize) {
                                    if (videoSize.width > 0 && videoSize.height > 0) {
                                        videoRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                                    }
                                }
                            })
                        }
                    }

                    DisposableEffect(exoPlayer) {
                        onDispose { exoPlayer.release() }
                    }

                    val exoLoading by rememberVideoLoadingState(exoPlayer)
                    PlayerLifecycleEffect(exoPlayer)

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                // FIT inside a full-screen surface: the frame is shown
                                // whole and as large as the screen allows, portrait or
                                // landscape, with no crop.
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                player = exoPlayer
                            }
                        },
                        update = { it.player = exoPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                    VideoLoadingOverlay(
                        visible = exoLoading,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Close & title overlay (hidden while fullscreen for an unobstructed view)
                if (!isFullscreen) {
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
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
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

                // Fullscreen enter/exit toggle
                if (!videoUrl.isNullOrBlank()) {
                    FullscreenToggleButton(
                        isFullscreen = isFullscreen,
                        onToggle = { isFullscreen = !isFullscreen },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
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
