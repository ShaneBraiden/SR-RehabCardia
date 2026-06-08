// PopupHelper.kt — Lightweight toast popup helper for action feedback
package com.srcardiocare.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a remembered toast function that shows a short Android Toast.
 *
 * Usage:
 *   val toast = rememberToast()
 *   toast("Workout added")
 *
 * Toasts auto-dismiss, don't require Scaffold wiring, and work from any composable.
 * Use for ephemeral action feedback like "Saved", "Removed", "Sent".
 */
@Composable
fun rememberToast(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Returns a remembered toast function with selectable duration.
 *
 * Usage:
 *   val toast = rememberLongToast()
 *   toast("Failed to save: network error")  // shows LONG
 */
@Composable
fun rememberLongToast(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
