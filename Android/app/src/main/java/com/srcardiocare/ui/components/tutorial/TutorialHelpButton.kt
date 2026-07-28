// TutorialHelpButton.kt — "?" action that replays the current screen's tour.
package com.srcardiocare.ui.components.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.srcardiocare.R

/**
 * A help "?" button that (re)starts the ambient [TutorialController]'s tour.
 * Drop into a `TopAppBar`'s `actions` (or a custom header). No-op when there is
 * no tour host in scope.
 *
 * @param tint icon tint; defaults to inheriting from the surrounding content.
 */
@Composable
fun TutorialHelpButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val controller = LocalTutorialController.current ?: return
    IconButton(
        onClick = { controller.start() },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.tutorial_show),
            tint = tint
        )
    }
}
