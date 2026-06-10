// FirebaseClients.kt — Shared Firebase SDK singletons for the data layer.
package com.srcardiocare.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/** Shared Firebase SDK entry points used by the repository objects. */
internal object FirebaseClients {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    val storage: FirebaseStorage = FirebaseStorage.getInstance()
}
