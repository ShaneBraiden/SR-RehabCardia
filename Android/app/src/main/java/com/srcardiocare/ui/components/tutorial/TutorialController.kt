// TutorialController.kt — State holder + target registry for guided beacon tours.
package com.srcardiocare.ui.components.tutorial

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Drives one screen's guided tour: which step is showing, whether it's active,
 * and the on-screen bounds of every registered target.
 *
 * Bounds are stored in root coordinates (px) so the overlay — drawn inside the
 * host's root [androidx.compose.foundation.layout.Box] — lines up regardless of
 * Scaffold/content padding.
 */
@Stable
class TutorialController(val steps: List<TutorialStep>) {

    /** targetId -> bounds in root coordinates (px). */
    val targets = mutableStateMapOf<String, Rect>()

    var currentIndex by mutableIntStateOf(0)
        private set

    var isActive by mutableStateOf(false)
        private set

    val stepCount: Int get() = steps.size

    val currentStep: TutorialStep? get() = steps.getOrNull(currentIndex)

    /** Bounds of the current step's target, or null if not yet positioned. */
    val currentTargetRect: Rect? get() = currentStep?.let { targets[it.targetId] }

    /** Whether at least one step's target has been positioned. */
    val hasAnyPositionedTarget: Boolean
        get() = steps.any { targets.containsKey(it.targetId) }

    fun register(id: String, rect: Rect) {
        targets[id] = rect
    }

    fun unregister(id: String) {
        targets.remove(id)
    }

    fun start() {
        if (steps.isEmpty()) return
        currentIndex = 0
        isActive = true
    }

    fun next() {
        if (currentIndex < steps.lastIndex) {
            currentIndex++
        } else {
            stop()
        }
    }

    fun back() {
        if (currentIndex > 0) currentIndex--
    }

    fun stop() {
        isActive = false
        currentIndex = 0
    }
}

/**
 * Provides the active [TutorialController] to descendants so [tutorialTarget]
 * and [com.srcardiocare.ui.components.tutorial.TutorialHelpButton] can reach it.
 * Null when no tour host is present (modifiers become no-ops).
 */
val LocalTutorialController = compositionLocalOf<TutorialController?> { null }

/**
 * Registers this composable's root-space bounds with the ambient
 * [TutorialController] under [id] so a tour step can place its beacon over it.
 * No-op when there is no controller in scope. Unregisters on dispose.
 */
fun Modifier.tutorialTarget(id: String): Modifier = composed {
    val controller = LocalTutorialController.current
    if (controller == null) {
        this
    } else {
        androidx.compose.runtime.DisposableEffect(controller, id) {
            onDispose { controller.unregister(id) }
        }
        this.onGloballyPositioned { coords ->
            controller.register(id, coords.boundsInRoot())
        }
    }
}
