// PlanRepository.kt — Exercise plans and plan-based exercise assignment.
package com.srcardiocare.data.firebase

import android.util.Log
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/** Manages `plans` documents and the higher-level "assign exercise" workflow. */
object PlanRepository {

    private const val TAG = "PlanRepository"

    suspend fun fetchPlans(patientId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("plans")
            .whereEqualTo("patientId", patientId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /**
     * One patient's plans as the responsible doctor.
     *
     * `plans` authorises a doctor per document via the denormalised `doctorId`,
     * exactly like `assignments`. Querying on `patientId` alone therefore fails
     * *wholesale* — not partially — the moment the patient holds a plan written
     * by anyone else: a previous clinician after a reassignment, or a seeded
     * plan carrying no `doctorId` at all. That denial is what surfaced as
     * "failing to add exercise": the read at the top of the assign flow threw,
     * so the prescription never got as far as being written.
     *
     * Constraining on `doctorId` too returns the caller's own plans instead of
     * failing the whole read.
     */
    suspend fun fetchPlansForDoctor(
        patientId: String,
        doctorId: String
    ): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("plans")
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("doctorId", doctorId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
    }

    /**
     * Plans for [patientId] visible to [viewerId] in [viewerRole]. Admins and
     * the patient themselves may read the whole set; a doctor is scoped to the
     * plans they wrote.
     */
    suspend fun fetchPlansFor(
        patientId: String,
        viewerId: String,
        viewerRole: String
    ): List<Pair<String, Map<String, Any?>>> =
        if (viewerRole == "doctor" && viewerId != patientId) {
            fetchPlansForDoctor(patientId, viewerId)
        } else {
            fetchPlans(patientId)
        }

    suspend fun createPlan(data: Map<String, Any>): String {
        val ref = FirebaseClients.db.collection("plans").document()
        val mutableData = data.toMutableMap()
        mutableData["id"] = ref.id
        mutableData["createdAt"] = FieldValue.serverTimestamp()
        ref.set(mutableData).await()
        return ref.id
    }

    /**
     * Assigns an exercise to a patient with prescription dates.
     *
     * The standalone Assignment is written first, because that is the document
     * the patient app actually reads. The `plans` mirror is legacy bookkeeping
     * and is best-effort: a patient whose plan history belongs to a previous
     * clinician must still be prescribable, and losing the mirror is a far
     * smaller problem than losing the prescription.
     */
    suspend fun assignExerciseToPatientWithPrescription(
        patientId: String,
        exerciseData: Map<String, Any>,
        expiryDays: Int,
        expiryDate: String,
        dailyFrequency: Int = 1
    ) {
        val doctorId = AuthRepository.currentUID ?: throw Exception("Not authenticated")

        val assignmentData = hashMapOf<String, Any>(
            "patientId" to patientId,
            "doctorId" to doctorId,
            "exerciseId" to (exerciseData["exerciseId"] ?: ""),
            "exerciseName" to (exerciseData["name"] ?: ""),
            "exerciseVideoUrl" to (exerciseData["videoUrl"] ?: ""),
            "exerciseCategory" to (exerciseData["category"] ?: ""),
            "exerciseGroup" to (exerciseData["group"] ?: ""),
            "exerciseDifficulty" to (exerciseData["difficulty"] ?: ""),
            "startDate" to java.time.LocalDate.now().toString(),
            "endDate" to expiryDate,
            "dailyFrequency" to dailyFrequency,
            "sets" to (exerciseData["customSets"] ?: exerciseData["sets"] ?: 3),
            "reps" to (exerciseData["customReps"] ?: exerciseData["reps"] ?: 10),
            "restSeconds" to (exerciseData["restSeconds"] ?: 45),
            "instructions" to (exerciseData["instructions"] ?: ""),
            "completionThreshold" to 1.0f,
            "isActive" to true
        )
        AssignmentRepository.createAssignment(assignmentData)

        mirrorToPlan(patientId, doctorId, exerciseData, expiryDays, expiryDate)

        val exerciseName = exerciseData["name"]?.toString()
            ?: exerciseData["title"]?.toString()
            ?: "a new exercise"
        com.srcardiocare.core.push.Notifier.send(
            com.srcardiocare.core.push.NotificationEvent.PrescriptionUpdated(
                patientId = patientId,
                exerciseName = exerciseName,
                expiryDate = expiryDate
            )
        )
    }

    /**
     * Best-effort write of the exercise into the patient's legacy active plan.
     * Never throws — see [assignExerciseToPatientWithPrescription].
     */
    private suspend fun mirrorToPlan(
        patientId: String,
        doctorId: String,
        exerciseData: Map<String, Any>,
        expiryDays: Int,
        expiryDate: String
    ) {
        runCatching {
            val role = runCatching { AuthRepository.claimedRole() }.getOrDefault("doctor")
            val plans = fetchPlansFor(patientId, doctorId, role)
            // Only a plan this caller owns can be updated — `allow update` on
            // `plans` is gated on the record's own doctorId.
            val activePlan = plans.firstOrNull {
                (it.second["isActive"] as? Boolean) == true &&
                    (role == "admin" || it.second["doctorId"] == doctorId)
            }

            if (activePlan != null) {
                FirebaseClients.db.collection("plans").document(activePlan.first).update(
                    mapOf(
                        "exercises" to FieldValue.arrayUnion(exerciseData),
                        "expiryDays" to expiryDays,
                        "expiryDate" to expiryDate
                    )
                ).await()
            } else {
                val planData = hashMapOf<String, Any>(
                    "patientId" to patientId,
                    "doctorId" to doctorId,
                    "isActive" to true,
                    "exercises" to listOf(exerciseData),
                    "expiryDays" to expiryDays,
                    "expiryDate" to expiryDate,
                    "startDate" to java.time.LocalDate.now().toString()
                )
                createPlan(planData)
            }
        }.onFailure { Log.w(TAG, "Plan mirror failed for patient $patientId", it) }
    }

    /** Removes a specific exercise from a patient's active plan. */
    suspend fun removeExerciseFromPlan(
        patientId: String,
        exerciseData: Map<String, Any>
    ) {
        val doctorId = AuthRepository.currentUID ?: return
        val role = runCatching { AuthRepository.claimedRole() }.getOrDefault("doctor")
        val plans = runCatching { fetchPlansFor(patientId, doctorId, role) }.getOrDefault(emptyList())
        val activePlan = plans.firstOrNull { (it.second["isActive"] as? Boolean) == true }

        if (activePlan != null) {
            val planId = activePlan.first
            FirebaseClients.db.collection("plans").document(planId).update(
                "exercises", FieldValue.arrayRemove(exerciseData)
            ).await()
        }
    }
}
