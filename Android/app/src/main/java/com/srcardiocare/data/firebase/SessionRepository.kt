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
        // Denormalised so the doctor's caseload query can be authorised per
        // document without a lookup. Sourced from the caller's own user
        // document, and the security rules check it against that same field
        // server-side — so this cannot be pointed at another clinician.
        val doctorId = AuthRepository.assignedDoctorId()
        val ref = FirebaseClients.db.collection("sessionLogs").document()
        val data = hashMapOf<String, Any?>(
            "id" to ref.id,
            "assignmentId" to assignmentId,
            "patientId" to patientId,
            "doctorId" to doctorId,
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

    /**
     * Fetch sessions for a specific assignment and date.
     *
     * [patientId] is required. The `sessionLogs` list rule authorises a query
     * per returned document — a patient only ever passes on their own
     * `patientId`, and an unconstrained query over the whole collection is
     * rejected wholesale (it would return other patients' logs). This query
     * used to filter on `assignmentId` alone, which meant every caller silently
     * caught a permission-denied and counted zero completed sessions, so the
     * doctor and admin dashboards showed every patient as behind schedule.
     */
    suspend fun fetchSessionsForDate(
        patientId: String,
        assignmentId: String,
        sessionDate: String
    ): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("assignmentId", assignmentId)
            .whereEqualTo("sessionDate", sessionDate)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /**
     * Fetch all sessions for an assignment (for history/stats).
     * [patientId] is required so the query passes the Firestore list rule
     * (patients may only list sessionLogs constrained to their own patientId).
     */
    suspend fun fetchAllSessionsForAssignment(
        patientId: String,
        assignmentId: String
    ): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("assignmentId", assignmentId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /**
     * As [fetchAllSessionsForAssignment], but for a doctor reading someone
     * else's logs.
     *
     * A patient's own `patientId` satisfies the list rule; a doctor's does not.
     * Their branch of the rule authorises per document on the denormalised
     * `doctorId`, so a query that does not constrain it is rejected wholesale —
     * the same trap `assignments` and `plans` already carry a doctor-scoped
     * read for. Without this the clinician screens silently caught the denial
     * and rendered every prescription as never once completed.
     */
    suspend fun fetchAllSessionsForAssignmentAsDoctor(
        patientId: String,
        assignmentId: String,
        doctorId: String
    ): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("assignmentId", assignmentId)
            .whereEqualTo("doctorId", doctorId)
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

    /** As [fetchTodaysSessions], scoped for a doctor — see [fetchAllSessionsForAssignmentAsDoctor]. */
    suspend fun fetchTodaysSessionsAsDoctor(
        patientId: String,
        doctorId: String
    ): List<Pair<String, Map<String, Any?>>> {
        val today = java.time.LocalDate.now().toString()
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("sessionDate", today)
            .whereEqualTo("doctorId", doctorId)
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

    /**
     * Get session count for a specific assignment on a specific date.
     * [patientId] is required for the same reason as [fetchSessionsForDate].
     */
    suspend fun getCompletedSessionCount(
        patientId: String,
        assignmentId: String,
        sessionDate: String
    ): Int {
        val snapshot = FirebaseClients.db.collection("sessionLogs")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("assignmentId", assignmentId)
            .whereEqualTo("sessionDate", sessionDate)
            .whereEqualTo("status", "COMPLETED")
            .get().await()
        return snapshot.documents.size
    }

    // ── Typed reads ─────────────────────────────────────────────────────

    suspend fun getSessionsForDate(
        patientId: String,
        assignmentId: String,
        sessionDate: String
    ): List<SessionLog> =
        fetchSessionsForDate(patientId, assignmentId, sessionDate)
            .map { (id, data) -> data.toSessionLog(id) }

    suspend fun getAllSessionsForAssignment(patientId: String, assignmentId: String): List<SessionLog> =
        fetchAllSessionsForAssignment(patientId, assignmentId).map { (id, data) -> data.toSessionLog(id) }

    suspend fun getTodaysSessions(patientId: String): List<SessionLog> =
        fetchTodaysSessions(patientId).map { (id, data) -> data.toSessionLog(id) }

    /**
     * [patientId]'s sessions for one assignment, as seen by [viewerId] in
     * [viewerRole]. Admins and the patient themselves may read the whole set;
     * a doctor is scoped to the logs stamped with their own id.
     */
    suspend fun getAllSessionsForAssignmentFor(
        patientId: String,
        assignmentId: String,
        viewerId: String,
        viewerRole: String
    ): List<SessionLog> =
        if (viewerRole == "doctor" && viewerId != patientId) {
            fetchAllSessionsForAssignmentAsDoctor(patientId, assignmentId, viewerId)
                .map { (id, data) -> data.toSessionLog(id) }
        } else {
            getAllSessionsForAssignment(patientId, assignmentId)
        }

    /** Today's sessions for [patientId] as seen by [viewerId] — see [getAllSessionsForAssignmentFor]. */
    suspend fun getTodaysSessionsFor(
        patientId: String,
        viewerId: String,
        viewerRole: String
    ): List<SessionLog> =
        if (viewerRole == "doctor" && viewerId != patientId) {
            fetchTodaysSessionsAsDoctor(patientId, viewerId).map { (id, data) -> data.toSessionLog(id) }
        } else {
            getTodaysSessions(patientId)
        }

    suspend fun getInProgressSession(patientId: String): SessionLog? =
        findInProgressSession(patientId)?.let { (id, data) -> data.toSessionLog(id) }
}
