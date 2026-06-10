// PlanRepository.kt — Exercise plans and plan-based exercise assignment.
package com.srcardiocare.data.firebase

import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/** Manages `plans` documents and the higher-level "assign exercise" workflow. */
object PlanRepository {

    suspend fun fetchPlans(patientId: String): List<Pair<String, Map<String, Any?>>> {
        val snapshot = FirebaseClients.db.collection("plans")
            .whereEqualTo("patientId", patientId)
            .get().await()
        return snapshot.documents.map { it.id to (it.data ?: emptyMap()) }
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
     * Assigns an exercise to a patient by adding it to their active plan.
     * If no active plan exists, creates one first.
     */
    suspend fun assignExerciseToPatient(
        patientId: String,
        exerciseData: Map<String, Any>
    ) {
        val doctorId = AuthRepository.currentUID ?: throw Exception("Not authenticated")
        val plans = fetchPlans(patientId)
        val activePlan = plans.firstOrNull { (it.second["isActive"] as? Boolean) == true }

        if (activePlan != null) {
            // Append exercise to existing plan
            val planId = activePlan.first
            FirebaseClients.db.collection("plans").document(planId).update(
                "exercises", FieldValue.arrayUnion(exerciseData)
            ).await()
        } else {
            // Create new active plan with this exercise
            val planData = hashMapOf<String, Any>(
                "patientId" to patientId,
                "doctorId" to doctorId,
                "isActive" to true,
                "exercises" to listOf(exerciseData)
            )
            createPlan(planData)
        }

        // Also create a standalone Assignment for the new assignment-based system
        val assignmentData = hashMapOf<String, Any>(
            "patientId" to patientId,
            "doctorId" to doctorId,
            "exerciseId" to (exerciseData["exerciseId"] ?: ""),
            "exerciseName" to (exerciseData["name"] ?: ""),
            "exerciseVideoUrl" to (exerciseData["videoUrl"] ?: ""),
            "exerciseCategory" to (exerciseData["category"] ?: ""),
            "exerciseDifficulty" to (exerciseData["difficulty"] ?: ""),
            "startDate" to java.time.LocalDate.now().toString(),
            "endDate" to java.time.LocalDate.now().plusDays(7).toString(), // Default to 7 days
            "dailyFrequency" to 1,
            "sets" to (exerciseData["customSets"] ?: exerciseData["sets"] ?: 3),
            "reps" to (exerciseData["customReps"] ?: exerciseData["reps"] ?: 10),
            "restSeconds" to (exerciseData["restSeconds"] ?: 45),
            "instructions" to (exerciseData["instructions"] ?: ""),
            "completionThreshold" to 1.0f,
            "isActive" to true
        )
        AssignmentRepository.createAssignment(assignmentData)

        val exerciseName = exerciseData["name"]?.toString()
            ?: exerciseData["title"]?.toString()
            ?: "a new exercise"
        com.srcardiocare.core.push.Notifier.send(
            com.srcardiocare.core.push.NotificationEvent.ExerciseAssigned(
                patientId = patientId,
                exerciseName = exerciseName
            )
        )
    }

    /**
     * Assigns an exercise to a patient with prescription dates.
     * Creates or updates the active plan with expiry information.
     */
    suspend fun assignExerciseToPatientWithPrescription(
        patientId: String,
        exerciseData: Map<String, Any>,
        expiryDays: Int,
        expiryDate: String
    ) {
        val doctorId = AuthRepository.currentUID ?: throw Exception("Not authenticated")
        val plans = fetchPlans(patientId)
        val activePlan = plans.firstOrNull { (it.second["isActive"] as? Boolean) == true }

        if (activePlan != null) {
            // Update existing plan with exercise and prescription info
            val planId = activePlan.first
            FirebaseClients.db.collection("plans").document(planId).update(
                mapOf(
                    "exercises" to FieldValue.arrayUnion(exerciseData),
                    "expiryDays" to expiryDays,
                    "expiryDate" to expiryDate
                )
            ).await()
        } else {
            // Create new active plan with prescription info
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

        // Also create a standalone Assignment for the new assignment-based system
        val assignmentData = hashMapOf<String, Any>(
            "patientId" to patientId,
            "doctorId" to doctorId,
            "exerciseId" to (exerciseData["exerciseId"] ?: ""),
            "exerciseName" to (exerciseData["name"] ?: ""),
            "exerciseVideoUrl" to (exerciseData["videoUrl"] ?: ""),
            "exerciseCategory" to (exerciseData["category"] ?: ""),
            "exerciseDifficulty" to (exerciseData["difficulty"] ?: ""),
            "startDate" to java.time.LocalDate.now().toString(),
            "endDate" to expiryDate,
            "dailyFrequency" to 1,
            "sets" to (exerciseData["customSets"] ?: exerciseData["sets"] ?: 3),
            "reps" to (exerciseData["customReps"] ?: exerciseData["reps"] ?: 10),
            "restSeconds" to (exerciseData["restSeconds"] ?: 45),
            "instructions" to (exerciseData["instructions"] ?: ""),
            "completionThreshold" to 1.0f,
            "isActive" to true
        )
        AssignmentRepository.createAssignment(assignmentData)

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

    /** Removes a specific exercise from a patient's active plan. */
    suspend fun removeExerciseFromPlan(
        patientId: String,
        exerciseData: Map<String, Any>
    ) {
        val plans = fetchPlans(patientId)
        val activePlan = plans.firstOrNull { (it.second["isActive"] as? Boolean) == true }

        if (activePlan != null) {
            val planId = activePlan.first
            FirebaseClients.db.collection("plans").document(planId).update(
                "exercises", FieldValue.arrayRemove(exerciseData)
            ).await()
        }
    }
}
