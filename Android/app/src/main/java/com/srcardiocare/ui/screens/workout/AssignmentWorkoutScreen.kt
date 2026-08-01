// AssignmentWorkoutScreen.kt — Workout-app style player: DEMO → ACTIVE → REST loop
package com.srcardiocare.ui.screens.workout

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.srcardiocare.R
import com.srcardiocare.core.security.ErrorHandler
import com.srcardiocare.data.firebase.FirebaseService
import com.srcardiocare.data.model.Assignment
import com.srcardiocare.ui.components.AppLogoWatermark
import com.srcardiocare.ui.components.FullscreenToggleButton
import com.srcardiocare.ui.components.FullscreenVideoEffect
import com.srcardiocare.ui.components.rememberToast
import com.srcardiocare.ui.components.tutorial.TutorialHelpButton
import com.srcardiocare.ui.components.tutorial.TutorialHost
import com.srcardiocare.ui.components.tutorial.TutorialIds
import com.srcardiocare.ui.components.tutorial.TutorialKeys
import com.srcardiocare.ui.components.tutorial.TutorialTours
import com.srcardiocare.ui.components.tutorial.tutorialTarget
import com.srcardiocare.ui.theme.DesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Phase { DEMO, ACTIVE, REST, ALL_DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentWorkoutScreen(
    assignment: Assignment,
    sessionNumber: Int,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    val totalSets = assignment.sets.coerceAtLeast(1)
    val reps = assignment.reps.coerceAtLeast(1)
    val restSeconds = assignment.restSeconds.coerceIn(5, 600)
    val videoUrl = assignment.exerciseVideoUrl
    val isYoutube = remember(videoUrl) { !extractYoutubeVideoId(videoUrl.orEmpty()).isNullOrBlank() }

    // Session state
    var sessionId by remember { mutableStateOf<String?>(null) }
    var currentSet by remember { mutableIntStateOf(1) }
    var phase by remember { mutableStateOf(Phase.DEMO) }
    var setStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var restRemaining by remember { mutableIntStateOf(restSeconds) }
    var showAbandonDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVideoFullscreen by remember { mutableStateOf(false) }

    // Keep the screen on, and block screenshots / screen recording (anti-piracy) for the session.
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Start session on first composition
    LaunchedEffect(assignment.id, sessionNumber) {
        try {
            sessionId = FirebaseService.startSession(
                assignmentId = assignment.id,
                sessionDate = LocalDate.now().toString(),
                sessionNumber = sessionNumber,
                totalSets = totalSets
            )
        } catch (e: Exception) {
            errorMessage = ErrorHandler.getDisplayMessage(e, "start session")
        }
    }

    // Demo video's native aspect ratio (updated once the video size is known).
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    // Sound is ON by default so the patient hears the exercise cues; the speaker
    // button on the player toggles it. The WebView is allowed to autoplay with
    // audio because mediaPlaybackRequiresUserGesture is disabled on it.
    var isMuted by remember { mutableStateOf(false) }
    val playerHtml = remember(videoUrl, isMuted) { buildPlayerHtml(videoUrl, isMuted) }

    // Looping demo video (no controls)
    val exoPlayer = remember(videoUrl) {
        if (videoUrl.isNullOrBlank() || isYoutube) null
        else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ALL
            // Request audio focus so playback ducks/pauses for calls and other apps.
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                    }
                }
            })
        }
    }
    LaunchedEffect(exoPlayer, isMuted) { exoPlayer?.volume = if (isMuted) 0f else 1f }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer?.release() } }

    // Rest-countdown ticker
    LaunchedEffect(phase) {
        if (phase == Phase.REST) {
            restRemaining = restSeconds
            while (restRemaining > 0 && phase == Phase.REST) {
                delay(1000)
                restRemaining--
                if (restRemaining == 3) playBeep(short = true)
            }
            if (phase == Phase.REST) {
                playBeep(short = false)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentSet++
                phase = Phase.ACTIVE
                setStartedAtMs = System.currentTimeMillis()
            }
        }
    }

    BackHandler(enabled = phase != Phase.DEMO || currentSet > 1) {
        showAbandonDialog = true
    }

    // While fullscreen, Back exits fullscreen instead of leaving the workout.
    BackHandler(enabled = isVideoFullscreen) { isVideoFullscreen = false }

    if (showAbandonDialog) {
        AbandonDialog(
            onConfirm = {
                showAbandonDialog = false
                scope.launch {
                    sessionId?.let { runCatching { FirebaseService.abandonSession(it) } }
                    toast(context.getString(R.string.workout_abandoned_toast))
                    onBack()
                }
            },
            onDismiss = { showAbandonDialog = false }
        )
    }

    if (phase == Phase.ALL_DONE) {
        AllDonePopup(
            exerciseName = assignment.exerciseName,
            sessionNumber = sessionNumber,
            onComplete = {
                scope.launch {
                    sessionId?.let { runCatching { FirebaseService.completeSession(it) } }
                    toast(context.getString(R.string.workout_completed_toast))
                    onComplete()
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    TutorialHost(tourKey = TutorialKeys.ASSIGNMENT_WORKOUT, steps = TutorialTours.assignmentWorkout) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            assignment.exerciseName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            stringResource(R.string.workout_header, sessionNumber, currentSet, totalSets),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showAbandonDialog = true }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.workout_end_desc))
                    }
                },
                actions = { TutorialHelpButton() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SetProgressBar(currentSet, totalSets, phase)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (phase) {
                    Phase.DEMO, Phase.ACTIVE -> ExerciseStage(
                        phase = phase,
                        videoUrl = videoUrl,
                        isYoutube = isYoutube,
                        playerHtml = playerHtml,
                        exoPlayer = exoPlayer,
                        videoAspectRatio = videoAspectRatio,
                        currentSet = currentSet,
                        totalSets = totalSets,
                        reps = reps,
                        instructions = assignment.instructions,
                        isFullscreen = isVideoFullscreen,
                        onToggleFullscreen = { isVideoFullscreen = true },
                        isMuted = isMuted,
                        onToggleMute = { isMuted = !isMuted },
                        videoModifier = Modifier.tutorialTarget(TutorialIds.AW_VIDEO),
                        repModifier = Modifier.tutorialTarget(TutorialIds.AW_REP_TARGET)
                    )
                    Phase.REST -> RestStage(
                        remaining = restRemaining,
                        total = restSeconds,
                        nextSet = currentSet + 1,
                        totalSets = totalSets
                    )
                    Phase.ALL_DONE -> {}
                }
            }

            BottomActionBar(
                phase = phase,
                buttonModifier = Modifier.tutorialTarget(TutorialIds.AW_PRIMARY),
                onPrimary = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    when (phase) {
                        Phase.DEMO -> {
                            phase = Phase.ACTIVE
                            setStartedAtMs = System.currentTimeMillis()
                        }
                        Phase.ACTIVE -> {
                            val secs = setStartedAtMs?.let {
                                ((System.currentTimeMillis() - it) / 1000L).toInt().coerceIn(0, 86400)
                            } ?: 0
                            scope.launch {
                                sessionId?.let { id ->
                                    runCatching {
                                        FirebaseService.logSetCompletion(
                                            sessionId = id,
                                            setNumber = currentSet,
                                            videoWatchedSeconds = secs,
                                            repsCompleted = reps
                                        )
                                    }
                                }
                            }
                            phase = if (currentSet < totalSets) Phase.REST else Phase.ALL_DONE
                        }
                        Phase.REST -> {
                            // Skip rest
                            restRemaining = 0
                        }
                        Phase.ALL_DONE -> {}
                    }
                }
            )
        }
    }
    }

    // Fullscreen video overlay — rotates to match the video and fills the screen.
    if (isVideoFullscreen) {
        FullscreenVideoEffect(active = true, isLandscapeVideo = videoAspectRatio >= 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoSurface(
                phase = phase,
                videoUrl = videoUrl,
                isYoutube = isYoutube,
                playerHtml = playerHtml,
                exoPlayer = exoPlayer,
                isFullscreen = true,
                onToggleFullscreen = { isVideoFullscreen = false },
                isMuted = isMuted,
                onToggleMute = { isMuted = !isMuted },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    }

    errorMessage?.let { msg ->
        Snackbar(
            modifier = Modifier.padding(DesignTokens.Spacing.MD),
            action = { TextButton(onClick = { errorMessage = null }) { Text(stringResource(R.string.action_ok)) } }
        ) { Text(msg) }
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// Set progress bar (chip row + thin progress)
// ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetProgressBar(currentSet: Int, totalSets: Int, phase: Phase) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.XL, vertical = DesignTokens.Spacing.SM),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSets) { i ->
            val setNum = i + 1
            val done = setNum < currentSet || (setNum == currentSet && phase == Phase.REST)
            val active = setNum == currentSet && phase != Phase.REST
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            done -> DesignTokens.Colors.Success
                            active -> DesignTokens.Colors.Primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// Video surface — player + corner tag + watermark + fullscreen toggle.
// Reused inline and inside the fullscreen overlay (only mounted in one at a time,
// so the shared ExoPlayer/WebView instance hands off without re-buffering).
// ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoSurface(
    phase: Phase,
    videoUrl: String?,
    isYoutube: Boolean,
    playerHtml: String,
    exoPlayer: ExoPlayer?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black)) {
        when {
            videoUrl.isNullOrBlank() -> NoVideoPlaceholder()
            isYoutube -> YouTubeWebPlayer(html = playerHtml, modifier = Modifier.fillMaxSize())
            exoPlayer != null -> AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        // Container matches the video's ratio, so FIT fills it with no bars and no crop.
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tag in corner: "DEMO" vs "GO"
        val tagText = stringResource(
            if (phase == Phase.ACTIVE) R.string.workout_tag_go else R.string.workout_tag_demo
        )
        val tagColor = if (phase == Phase.ACTIVE) DesignTokens.Colors.Success else DesignTokens.Colors.Primary
        Surface(
            shape = RoundedCornerShape(50),
            color = tagColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(DesignTokens.Spacing.SM)
        ) {
            Text(
                tagText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (!videoUrl.isNullOrBlank()) {
            // Sound + fullscreen toggles (top-right).
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(DesignTokens.Spacing.SM),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.SM)
            ) {
                Surface(
                    onClick = onToggleMute,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = stringResource(
                            if (isMuted) R.string.workout_sound_on else R.string.workout_sound_off
                        ),
                        tint = Color.White,
                        modifier = Modifier.padding(7.dp)
                    )
                }
                FullscreenToggleButton(
                    isFullscreen = isFullscreen,
                    onToggle = onToggleFullscreen
                )
            }

            // App-logo watermark (branding + anti-piracy deterrent)
            AppLogoWatermark(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(DesignTokens.Spacing.SM)
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// DEMO / ACTIVE stage — looping demo + big rep target
// ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExerciseStage(
    phase: Phase,
    videoUrl: String?,
    isYoutube: Boolean,
    playerHtml: String,
    exoPlayer: ExoPlayer?,
    videoAspectRatio: Float,
    currentSet: Int,
    totalSets: Int,
    reps: Int,
    instructions: String?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    videoModifier: Modifier = Modifier,
    repModifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DesignTokens.Spacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video panel — sized to the video's true aspect ratio (whole frame, no crop),
        // capped at half the area so the rep counter stays visible.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val maxVideoHeight = maxHeight * 0.5f
            val widthLimitedHeight = maxWidth / videoAspectRatio
            val videoW = if (widthLimitedHeight <= maxVideoHeight) maxWidth else maxVideoHeight * videoAspectRatio
            val videoH = if (widthLimitedHeight <= maxVideoHeight) widthLimitedHeight else maxVideoHeight
            Box(
                modifier = videoModifier
                    .width(videoW)
                    .height(videoH)
                    .clip(RoundedCornerShape(DesignTokens.Radius.Card))
                    .background(Color.Black)
            ) {
                // While fullscreen, the player is hosted by the fullscreen overlay
                // instead — this inline slot stays an empty black placeholder.
                if (!isFullscreen) {
                    VideoSurface(
                        phase = phase,
                        videoUrl = videoUrl,
                        isYoutube = isYoutube,
                        playerHtml = playerHtml,
                        exoPlayer = exoPlayer,
                        isFullscreen = false,
                        onToggleFullscreen = onToggleFullscreen,
                        isMuted = isMuted,
                        onToggleMute = onToggleMute,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))

        // Big rep target
        Text(
            "$reps",
            modifier = repModifier,
            fontSize = 96.sp,
            fontWeight = FontWeight.Black,
            color = if (phase == Phase.ACTIVE) DesignTokens.Colors.Success else DesignTokens.Colors.Primary
        )
        Text(
            stringResource(if (reps == 1) R.string.workout_rep_singular else R.string.workout_rep_plural),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))

        Text(
            stringResource(R.string.workout_set_of, currentSet, totalSets),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (!instructions.isNullOrBlank() && phase == Phase.DEMO) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.MD))
            Card(
                shape = RoundedCornerShape(DesignTokens.Radius.Base),
                colors = CardDefaults.cardColors(
                    containerColor = DesignTokens.Colors.Primary.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(DesignTokens.Spacing.MD),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = DesignTokens.Colors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
                    Text(
                        instructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun NoVideoPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.VideocamOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
            Text(stringResource(R.string.workout_no_demo_video), color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// REST stage — ring countdown
// ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun RestStage(remaining: Int, total: Int, nextSet: Int, totalSets: Int) {
    val progress by animateFloatAsState(
        targetValue = if (total > 0) remaining.toFloat() / total else 0f,
        label = "restRing"
    )
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.workout_tag_rest),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 6.sp
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 14.dp,
                color = DesignTokens.Colors.Primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$remaining",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Black,
                    color = DesignTokens.Colors.Primary
                )
                Text(
                    stringResource(R.string.workout_rest_seconds_suffix),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
        Text(
            stringResource(R.string.workout_up_next),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp
        )
        Text(
            stringResource(R.string.workout_set_of, nextSet, totalSets),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// Bottom primary action
// ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomActionBar(phase: Phase, onPrimary: () -> Unit, buttonModifier: Modifier = Modifier) {
    val (label, container, icon) = when (phase) {
        Phase.DEMO -> Triple(stringResource(R.string.workout_action_ready), DesignTokens.Colors.Primary, Icons.Default.PlayArrow)
        Phase.ACTIVE -> Triple(stringResource(R.string.workout_action_set_done), DesignTokens.Colors.Success, Icons.Default.Check)
        Phase.REST -> Triple(stringResource(R.string.workout_action_skip_rest), MaterialTheme.colorScheme.secondary, Icons.Default.SkipNext)
        Phase.ALL_DONE -> Triple(stringResource(R.string.workout_action_finishing), DesignTokens.Colors.Success, Icons.Default.Check)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 8.dp
    ) {
        Button(
            onClick = onPrimary,
            enabled = phase != Phase.ALL_DONE,
            modifier = buttonModifier
                .fillMaxWidth()
                .padding(
                    horizontal = DesignTokens.Spacing.XL,
                    vertical = DesignTokens.Spacing.MD
                )
                .height(64.dp),
            shape = RoundedCornerShape(DesignTokens.Radius.Button),
            colors = ButtonDefaults.buttonColors(containerColor = container)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.SM))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// Dialogs
// ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun AbandonDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_abandon_title)) },
        text = { Text(stringResource(R.string.workout_abandon_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.workout_abandon_confirm), color = DesignTokens.Colors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.workout_abandon_dismiss)) }
        }
    )
}

@Composable
private fun AllDonePopup(exerciseName: String, sessionNumber: Int, onComplete: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.Spacing.MD),
            shape = RoundedCornerShape(DesignTokens.Radius.XXL),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(DesignTokens.Spacing.XL),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(DesignTokens.Colors.Success.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = DesignTokens.Colors.Success
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.LG))
                Text(
                    stringResource(R.string.workout_complete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = DesignTokens.Colors.Success
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.SM))
                Text(
                    exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.workout_session_done, sessionNumber),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(DesignTokens.Spacing.XL))
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(DesignTokens.Radius.Button),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Colors.Success)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────────
// Sound + WebView helpers
// ───────────────────────────────────────────────────────────────────────────────

private fun playBeep(short: Boolean) {
    runCatching {
        val tone = if (short) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2
        val gen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        gen.startTone(tone, if (short) 120 else 350)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebPlayer(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context: Context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                // Below API 30 these default to true. The player only ever needs
                // remote YouTube content, so deny it any path to local files or
                // content:// providers.
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
                }
                tag = html
                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
            }
        },
        // Only reload when the document actually changed (e.g. the mute toggle).
        // Reloading on every recomposition would restart the demo video.
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
            }
        },
        modifier = modifier
    )
}

/**
 * Builds the WebView document for the demo player.
 *
 * [muted] drives both the YouTube `mute` parameter and the HTML5 `muted`
 * attribute. It defaults to false so the exercise audio plays — the WebView sets
 * `mediaPlaybackRequiresUserGesture = false`, which is what lets unmuted
 * autoplay through.
 */
private fun buildPlayerHtml(videoUrl: String?, muted: Boolean = false): String {
    if (videoUrl.isNullOrBlank()) {
        return """
            <html><body style="margin:0;padding:0;display:flex;align-items:center;justify-content:center;background:#000;color:#fff;font-family:sans-serif;">
              <div>No video available</div>
            </body></html>
        """.trimIndent()
    }
    // Validate ID contains only safe YouTube ID characters before embedding
    val videoId = extractYoutubeVideoId(videoUrl)
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_\\-]{1,20}")) }
    return if (videoId != null) {
        """
            <html>
            <head>
              <meta http-equiv="Content-Security-Policy"
                content="default-src 'none'; frame-src https://www.youtube.com https://www.youtube-nocookie.com; script-src 'none'; style-src 'unsafe-inline';">
            </head>
            <body style="margin:0;padding:0;background:#000;">
              <iframe width="100%" height="100%"
                src="https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&rel=0&autoplay=1&mute=${if (muted) 1 else 0}&loop=1&playlist=$videoId&controls=0&modestbranding=1"
                frameborder="0" allow="autoplay; encrypted-media; picture-in-picture">
              </iframe>
            </body></html>
        """.trimIndent()
    } else {
        // Only allow HTTPS URLs for non-YouTube video sources, URL-encoded so the
        // value cannot break out of the src attribute (HTML injection).
        if (!videoUrl.startsWith("https://")) return buildPlayerHtml(null)
        val encodedUrl = Uri.encode(videoUrl, ":/?&=%.\\-_~")
        val mutedAttr = if (muted) "muted" else ""
        """
            <html>
            <head>
              <meta http-equiv="Content-Security-Policy"
                content="default-src 'none'; media-src https:; script-src 'none'; style-src 'unsafe-inline';">
            </head>
            <body style="margin:0;padding:0;background:#000;">
              <video width="100%" height="100%" autoplay loop $mutedAttr playsinline>
                <source src="$encodedUrl" type="video/mp4" />
              </video>
            </body></html>
        """.trimIndent()
    }
}

private fun extractYoutubeVideoId(url: String): String? {
    return runCatching {
        val uri = Uri.parse(url)
        when {
            uri.host?.contains("youtu.be") == true -> uri.lastPathSegment
            uri.host?.contains("youtube.com") == true && uri.path?.startsWith("/embed/") == true ->
                uri.pathSegments.getOrNull(1)
            uri.host?.contains("youtube.com") == true -> uri.getQueryParameter("v")
            else -> null
        }
    }.getOrNull()
}
