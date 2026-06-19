// TutorialRepository.kt — Persistence for the guided beacon tours.
//
// Tours seen by the current account live in `users/{uid}.toursSeen` (a string
// array of tourKeys). To avoid a Firestore read on every screen, the set is
// seeded once into the in-memory [CurrentUserTours] cache (from the user-doc
// listener in NavGraph's CurrentUserDocGuard) and updated optimistically here.
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Process-wide cache of the signed-in user's tour state. Seeded once from the
 * user document so individual screens never hit Firestore just to decide
 * whether to auto-run a tour.
 */
object CurrentUserTours {
    private val _seen = MutableStateFlow<Set<String>>(emptySet())
    val seen: StateFlow<Set<String>> = _seen.asStateFlow()

    /** Whether first-run tours should auto-start for this account. */
    var autoToursEnabled: Boolean = false
        private set

    /** Seed from the user doc (called by the user-doc listener). */
    fun seed(toursSeen: Collection<String>, autoEnabled: Boolean) {
        _seen.value = toursSeen.toSet()
        autoToursEnabled = autoEnabled
    }

    /** Optimistically mark a tour as seen in the cache. */
    fun markSeenLocal(tourKey: String) {
        if (tourKey !in _seen.value) {
            _seen.value = _seen.value + tourKey
        }
    }

    /** Clear on logout / account switch so the next user starts fresh. */
    fun clear() {
        _seen.value = emptySet()
        autoToursEnabled = false
    }
}

/** Reads/writes the `toursSeen` list on the current user's document. */
object TutorialRepository {

    /**
     * Records that the current user has seen [tourKey]. Updates the in-memory
     * cache immediately, then persists via `arrayUnion` (idempotent). Failures
     * are swallowed — a missed write just means the tour may re-run later, which
     * is harmless.
     */
    suspend fun markTourSeen(tourKey: String) {
        CurrentUserTours.markSeenLocal(tourKey)
        val uid = AuthRepository.currentUID ?: return
        runCatching {
            FirebaseClients.db.collection("users").document(uid)
                .update("toursSeen", FieldValue.arrayUnion(tourKey))
                .await()
        }
    }
}
