// SessionRepository.kt — Exercise session logging with set-completion rate limiting.
package com.srcardiocare.data.firebase

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.srcardiocare.data.model.SessionLog
import kotlinx.coroutines.tasks.await

/** Exception thrown when a rate limit is exceeded. */
class RateLimitExceededException(message: String) : Exception(message)

/**
 * Rate limiter for tracking method call frequency.
 * Thread-safe implementation for concurrent access.
 */
private data class RateLimitBucket(
    val timestamps: MutableList<Long> = mutableListOf(),
    val maxCalls: Int,
    val windowMillis: Long
) {
    @Synchronized
    fun allowRequest(): Boolean {
        val now = System.currentTimeMillis()
        // Remove timestamps outside the time window
        timestamps.removeAll { it < now - windowMillis }

        return if (timestamps.size < maxCalls) {
            timestamps.add(now)
            true
        } else {
            false
        }
    }

    @Synchronized
    fun getNextAllowedTime(): Long {
        if (timestamps.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val oldestTimestamp = timestamps.minOrNull() ?: return 0
        return maxOf(0, (oldestTimestamp + windowMillis) - now)
    }
}

/** Logs per-session exercise activity and enforces set-completion rate limits. */
object SessionRepository {

    private const val TAG = "SessionRepository"

    // Rate limiting: 5 logSetCompletion calls per 15 minutes per session
    private val setCompletionRateLimiters = mutableMapOf<String, RateLimitBucket>()
    private const val SET_COMPLETION_MAX_CALLS = 5
    private const val SET_COMPLETION_WINDOW_MS = 15 * 60 * 1000L // 15 minutes
    private const val RATE_LIMITER_CLEANUP_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour
    private var lastRateLimiterCleanup = System.currentTimeMillis()

    /**
     * Periodically clean up stale rate limiter buckets to prevent memory leaks.
     * Called before adding new buckets.
     */
    private fun cleanupStaleRateLimiters() {
        val now = System.currentTimeMillis()
        if (now - lastRateLimiterCleanup < RATE_LIMITER_CLEANUP_THRESHOLD_MS) return

        synchronized(setCompletionRateLimiters) {
            val staleKeys = setCompletionRateLimiters.entries
                .filter { (_, bucket) ->
                    bucket.timestamps.isEmpty() ||
                        bucket.timestamps.all { it < now - RATE_LIMITER_CLEANUP_THRESHOLD_MS }
                }
                .map { it.key }

            staleKeys.forEach { setCompletionRateLimiters.remove(it) }
            lastRateLimiterCleanup = now

            if (staleKeys.isNotEmpty()) {
                Log.d(TAG, "Cleaned up ${staleKeys.size} stale rate limiter buckets")
            }
        }
    }

    /** Start a new exercise session. */
    suspend fun startSession(
        assignmentId: String,
        sessionDate: String,
        sessionNumber: Int,
        totalSets: Int
    ): String {
        val patientId = AuthRepository.currentUID ?: throw Exception("Not authenticated")
        val ref = FirebaseClients.db.collection("sessionLogs").document()
        val data = hashMapOf<String, Any?>(
            "id" to ref.id,
            "assignmentId" to assignmentId,
            "patientId" to patientId,
            "sessionDate" to sessionDate,
            "sessionNumber" to sessionNumber,
            "startedAt" to FieldValue.serverTimestamp(),
            "completedAt" to null,
            "setsCompleted" to 0,
            "totalSets" to totalSets,
            "setLogs" to emptyList<Map<String, Any>>(),
            "status" to "IN_PROGRESS",
            "feedbackId" to null
        )
        ref.set(data).await()
        return ref.id
    }

    /**
     * Log completion of a single set within a session.
     *
     * **Rate Limited:** 5 calls per 15 minutes per session to prevent abuse.
     * Throws RateLimitExceededException if limit is exceeded.
     */
    suspend fun logSetCompletion(
        sessionId: String,
        setNumber: Int,
        videoWatchedSeconds: Int,
        repsCompleted: Int?
    ) {
        // Input validation
        require(setNumber > 0) { "setNumber must be positive" }
        require(videoWatchedSeconds in 0..86400) { "videoWatchedSeconds must be 0-86400" }
        require(repsCompleted == null || repsCompleted > 0) { "repsCompleted must be positive if provided" }

        // Clean up stale rate limiters periodically
        cleanupStaleRateLimiters()

        // Rate limiting check
        val bucket = synchronized(setCompletionRateLimiters) {
            setCompletionRateLimiters.getOrPut(sessionId) {
                RateLimitBucket(
                    maxCalls = SET_COMPLETION_MAX_CALLS,
                    windowMillis = SET_COMPLETION_WINDOW_MS
                )
            }
        }

        if (!bucket.allowRequest()) {
            val waitTimeMs = bucket.getNextAllowedTime()
            val waitMinutes = (waitTimeMs / 60000.0).toInt()
            throw RateLimitExceededException(
                "Rate limit exceeded for logSetCompletion. " +
                "Maximum $SET_COMPLETION_MAX_CALLS calls per 15 minutes. " +
                "Please wait $waitMinutes minute(s) before trying again."
            )
        }

        // Use server timestamp for consistency with other Firestore operations
        val setLog = hashMapOf<String, Any?>(
            "setNumber" to setNumber,
            "completedAt" to FieldValue.serverTimestamp(),
            "videoWatchedSeconds" to videoWatchedSeconds,
            "repsCompleted" to repsCompleted
        )
        FirebaseClients.db.collection("sessionLogs").document(sessionId).update(
            mapOf(
                "setLogs" to FieldValue.arrayUnion(setLog),
                "setsCompleted" to setNumber
            )
        ).await()
    }

    /**
     * Complete a session (all sets done, user clicked Complete).
     * Cleans up rate limiter for this session.
     */
    suspend fun completeSession(sessionId: String, feedbackId: String? = null) {
        FirebaseClients.db.collection("sessionLogs").document(sessionId).update(
            mapOf(
                "status" to "COMPLETED",
                "completedAt" to FieldValue.serverTimestamp(),
                "feedbackId" to feedbackId
            )
        ).await()

        // Clean up rate limiter for completed session
        cleanupRateLimiter(sessionId)
    }

    /**
     * Mark a session as abandoned (started but not finished).
     * Cleans up rate limiter for this session.
     */
    suspend fun abandonSession(sessionId: String) {
        FirebaseClients.db.collection("sessionLogs").document(sessionId).update(
            mapOf(
                "status" to "ABANDONED",
                "completedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Clean up rate limiter for abandoned session
        cleanupRateLimiter(sessionId)
    }

    /** Clean up rate limiter bucket for a session to prevent memory leaks. */
    private fun cleanupRateLimiter(sessionId: String) {
        synchronized(setCompletionRateLimiters) {
            setCompletionRateLimiters.remove(sessionId)
        }
    }

    /** Fetch sessions for a specific assignment and date. */
    suspend fun fetchSessionsForDate(
        assignmentId: String,
        sessionDate: String
    ): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("assignmentId", assignmentId)
            .whereEqualTo("sessionDate", sessionDate)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /** Fetch all sessions for an assignment (for history/stats). */
    suspend fun fetchAllSessionsForAssignment(
        assignmentId: String
    ): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("assignmentId", assignmentId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /** Fetch today's sessions for a patient (across all assignments). */
    suspend fun fetchTodaysSessions(patientId: String): List<Pair<String, Map<String, Any?>>> {
        val today = java.time.LocalDate.now().toString()
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("sessionDate", today)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /** Find any in-progress session for a patient. */
    suspend fun findInProgressSession(patientId: String): Pair<String, Map<String, Any?>>? {
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("status", "IN_PROGRESS")
            .limit(1)
            .get().await()
        return snapshot.documents.firstOrNull()?.let { it.id to (it.data ?: emptyMap()) }
    }

    /** Get session count for a specific assignment on a specific date. */
    suspend fun getCompletedSessionCount(assignmentId: String, sessionDate: String): Int {
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("assignmentId", assignmentId)
            .whereEqualTo("sessionDate", sessionDate)
            .whereEqualTo("status", "COMPLETED")
            .get().await()
        return snapshot.documents.size
    }

    // ── Typed reads ─────────────────────────────────────────────────────

    suspend fun getSessionsForDate(assignmentId: String, sessionDate: String): List<SessionLog> =
        fetchSessionsForDate(assignmentId, sessionDate).map { (id, data) -> data.toSessionLog(id) }

    suspend fun getAllSessionsForAssignment(assignmentId: String): List<SessionLog> =
        fetchAllSessionsForAssignment(assignmentId).map { (id, data) -> data.toSessionLog(id) }

    suspend fun getTodaysSessions(patientId: String): List<SessionLog> =
        fetchTodaysSessions(patientId).map { (id, data) -> data.toSessionLog(id) }

    suspend fun getInProgressSession(patientId: String): SessionLog? =
        findInProgressSession(patientId)?.let { (id, data) -> data.toSessionLog(id) }
}
