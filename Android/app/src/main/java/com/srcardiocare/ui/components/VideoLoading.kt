// VideoLoading.kt — The "it's coming" state for every video surface in the app.
package com.srcardiocare.ui.components

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * A spinner over the black of a video surface that has not painted a frame yet.
 *
 * Every player in the app — ExoPlayer and the YouTube WebView alike — starts on
 * a black rectangle and stays there for as long as the network takes. Without
 * this the patient cannot tell a slow ward connection from a broken exercise,
 * and the usual response to a screen that looks dead is to back out of it.
 *
 * Fades rather than cuts, and the fade *out* matters more than the fade in: the
 * first video frame and the disappearance of the spinner land in the same frame
 * otherwise, which reads as a flicker.
 */
@Composable
fun VideoLoadingOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
                if (label != null) {
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/**
 * Pauses [player] while the screen is not in the foreground, and picks up where
 * it left off on return.
 *
 * Without this an ExoPlayer keeps decoding — video decode, audio output and the
 * network fetch behind them — for as long as the composable is alive, which
 * includes the entire time the app sits behind the launcher or a phone call.
 * The demo loop on the workout screen is the worst case: it repeats forever, so
 * a patient who backgrounds mid-exercise leaves a video decoding indefinitely.
 *
 * Only playback we paused is resumed, so a player the user deliberately paused
 * before leaving stays paused.
 */
@Composable
fun PlayerLifecycleEffect(player: ExoPlayer?) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(player, lifecycleOwner) {
        if (player == null) return@DisposableEffect onDispose { }

        var pausedByUs = false

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (player.isPlaying) {
                        pausedByUs = true
                        player.pause()
                    }
                }

                Lifecycle.Event.ON_START -> {
                    if (pausedByUs) {
                        pausedByUs = false
                        player.play()
                    }
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * The same treatment for a [WebView]-hosted player.
 *
 * A WebView left in the foreground state keeps its JavaScript timers, its media
 * element and its network fetches running after the app is backgrounded — the
 * YouTube embed will happily carry on playing audio behind the launcher.
 * `onPause` stops the page's own work; `pauseTimers` stops the JS scheduler,
 * which is process-wide and is what actually stops the CPU spinning.
 *
 * Both are safe to call unconditionally on the matching resume.
 */
@Composable
fun WebViewLifecycleEffect(webView: WebView?) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(webView, lifecycleOwner) {
        if (webView == null) return@DisposableEffect onDispose { }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    webView.onPause()
                    webView.pauseTimers()
                }

                Lifecycle.Event.ON_START -> {
                    webView.resumeTimers()
                    webView.onResume()
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Leaving the screen must stop playback outright, not just pause
            // it: the composable is gone but the WebView is not collected for
            // as long as anything still holds it.
            webView.onPause()
            webView.pauseTimers()
        }
    }
}

/**
 * Whether [player] currently has nothing to show.
 *
 * True while the player is idle or buffering *and* no frame has been rendered
 * yet, and true again on any later re-buffer. Keying off
 * [Player.Listener.onRenderedFirstFrame] rather than playback state alone is
 * what stops the spinner lingering over a picture that is already visible — a
 * player can report BUFFERING while the last decoded frame is still on screen.
 *
 * Returns false for a null player: there is no video to wait for.
 */
@Composable
fun rememberVideoLoadingState(player: ExoPlayer?): State<Boolean> {
    val loading = remember(player) { mutableStateOf(player != null) }

    DisposableEffect(player) {
        if (player == null) {
            loading.value = false
            return@DisposableEffect onDispose { }
        }

        var hasFrame = false

        fun recompute() {
            loading.value = when (player.playbackState) {
                Player.STATE_BUFFERING -> true
                Player.STATE_IDLE -> !hasFrame
                else -> false
            }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                recompute()
            }

            override fun onRenderedFirstFrame() {
                hasFrame = true
                recompute()
            }

            // A failed load must not leave the spinner turning forever; the
            // surface underneath shows the error, and a spinner on top of it
            // reads as "still trying" when nothing is trying.
            override fun onPlayerError(error: PlaybackException) {
                loading.value = false
            }
        }

        player.addListener(listener)
        recompute()

        onDispose { player.removeListener(listener) }
    }

    return loading
}
