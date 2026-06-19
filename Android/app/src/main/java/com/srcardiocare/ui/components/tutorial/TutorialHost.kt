// TutorialHost.kt — Wraps a screen with its guided tour controller + overlay.
package com.srcardiocare.ui.components.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.srcardiocare.data.firebase.CurrentUserTours
import com.srcardiocare.data.firebase.TutorialRepository
import kotlinx.coroutines.launch

/**
 * Hosts a guided beacon tour for one screen.
 *
 * Wrap the screen's root (Scaffold/Surface) in this. It:
 *  - remembers a [TutorialController] for [steps] and provides it via
 *    [LocalTutorialController] so `Modifier.tutorialTarget(...)` and
 *    `TutorialHelpButton()` inside [content] can reach it;
 *  - lays [content] and the (non-blocking) overlay in a single Box so beacon
 *    coordinates align with the content's root space;
 *  - auto-starts the tour for genuinely new accounts
 *    (`autoToursEnabled && tourKey !in seen`) once a target is positioned;
 *  - persists completion via [TutorialRepository.markTourSeen] on finish/skip.
 *
 * The "?" replay button works for everyone regardless of [tourKey] state.
 *
 * @param tourKey stable key identifying this screen's tour (see [TutorialIds]).
 */
@Composable
fun TutorialHost(
    tourKey: String,
    steps: List<TutorialStep>,
    content: @Composable () -> Unit
) {
    val controller = remember(tourKey) { TutorialController(steps) }
    val scope = rememberCoroutineScope()
    val seen by CurrentUserTours.seen.collectAsState()

    val markSeen: () -> Unit = {
        scope.launch { TutorialRepository.markTourSeen(tourKey) }
    }

    // Auto-start once for new accounts, after a target has been positioned so
    // the first beacon lands in the right place.
    val shouldAutoStart = CurrentUserTours.autoToursEnabled &&
        tourKey !in seen &&
        steps.isNotEmpty() &&
        controller.hasAnyPositionedTarget

    LaunchedEffect(shouldAutoStart) {
        if (shouldAutoStart && !controller.isActive && tourKey !in CurrentUserTours.seen.value) {
            controller.start()
        }
    }

    CompositionLocalProvider(LocalTutorialController provides controller) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            if (controller.isActive) {
                TutorialOverlay(
                    controller = controller,
                    onFinished = markSeen
                )
            }
        }
    }
}
