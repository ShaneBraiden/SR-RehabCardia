// ExerciseRepository.kt — Exercise catalog and exercise video media.
package com.srcardiocare.data.firebase

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.srcardiocare.data.model.Exercise
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** CRUD over the `exercises` collection plus exercise video upload to Storage. */
object ExerciseRepository {

    private const val TAG = "ExerciseRepository"

    suspend fun fetchExercises(category: String? = null): List<Pair<String, Map<String, Any?>>> {
        var query: Query = FirebaseClients.db.collection("exercises")
        if (category != null) {
            query = query.whereEqualTo("category", category)
        }
        val snapshot = query.get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    suspend fun createExercise(data: Map<String, Any>): String {
        val ref = FirebaseClients.db.collection("exercises").document()
        val mutableData = data.toMutableMap()
        mutableData["id"] = ref.id
        mutableData["createdAt"] = FieldValue.serverTimestamp()
        ref.set(mutableData).await()
        return ref.id
    }

    suspend fun deleteExercise(exerciseId: String, videoUrl: String?) {
        // Delete Firestore document
        FirebaseClients.db.collection("exercises").document(exerciseId).delete().await()

        // Delete Storage file if it exists
        if (!videoUrl.isNullOrBlank()) {
            try {
                // Get a reference from the URL
                val storageRef = FirebaseClients.storage.getReferenceFromUrl(videoUrl)
                storageRef.delete().await()
            } catch (e: Exception) {
                // Log error but don't fail the exercise deletion
                Log.e(TAG, "Failed to delete storage file: ${e.message}", e)
            }
        }
    }

    /** Uploads a video to Firebase Storage and returns the download URL. */
    suspend fun uploadVideo(
        data: ByteArray,
        mimeType: String = "video/mp4"
    ): String {
        AuthRepository.currentUID ?: throw Exception("Not authenticated")
        val storageRef = FirebaseClients.storage.reference
        val videoRef = storageRef.child("videos/${UUID.randomUUID()}.mp4")

        val metadata = storageMetadata {
            contentType = mimeType
        }

        // Upload the file
        videoRef.putBytes(data, metadata).await()

        // Get the download URL
        return videoRef.downloadUrl.await().toString()
    }

    /** Typed catalog read. Prefer this in new code. */
    suspend fun getExercises(category: String? = null): List<Exercise> =
        fetchExercises(category).map { (id, data) -> data.toExercise(id) }
}
