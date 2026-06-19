// TutorialModels.kt — Data model for a single step of a guided beacon tour.
package com.srcardiocare.ui.components.tutorial

/**
 * One step of a guided tour: a pulsing beacon over the control identified by
 * [targetId] plus a tip card showing [title] and [text].
 *
 * [targetId] must match the id passed to `Modifier.tutorialTarget(id)` on the
 * control to highlight. Both are sourced from [TutorialIds] for consistency.
 */
data class TutorialStep(
    val targetId: String,
    val title: String,
    val text: String
)
