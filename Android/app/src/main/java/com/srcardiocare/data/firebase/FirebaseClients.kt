// FirebaseClients.kt — Shared Firebase SDK singletons for the data layer.
package com.srcardiocare.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage

/** Shared Firebase SDK entry points used by the repository objects. */
internal object FirebaseClients {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Resolved per call rather than held once.
     *
     * Signing out clears Firestore's on-disk cache so the next account cannot
     * read the previous one's documents offline, and `clearPersistence()`
     * requires the instance to be terminated first. A terminated instance
     * throws on every later call, so a cached `val` here would hand every
     * repository in the app a dead client for the rest of the process.
     * `getInstance()` is a map lookup once the SDK holds one.
     */
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    /**
     * Callable Cloud Functions. Region must match the deployment in
     * `functions/index.js`, which uses the default us-central1.
     */
    val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
}
