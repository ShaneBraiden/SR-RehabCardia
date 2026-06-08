package com.srcardiocare.core.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-activity holder for a deep-link target captured from a push tap.
 *
 * [PushMessagingService] builds a PendingIntent whose extras carry the route
 * and params. [com.srcardiocare.MainActivity] reads those extras in
 * `onCreate` / `onNewIntent` and writes here via [queue]. The NavHost picks
 * the value up once it's composed and calls [consume] so the same tap never
 * routes twice.
 */
object PendingRoute {
    data class Target(val route: String, val params: Map<String, String>)

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending

    fun queue(route: String, params: Map<String, String>) {
        if (route.isBlank()) return
        _pending.value = Target(route, params)
    }

    fun consume(): Target? {
        val current = _pending.value ?: return null
        _pending.value = null
        return current
    }
}
