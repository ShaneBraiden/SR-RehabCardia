// UserMappers.kt — Firestore document -> User model mapping.
package com.srcardiocare.data.firebase

import com.google.firebase.Timestamp
import com.srcardiocare.data.model.User

/** Builds a [User] from a Firestore `users` document (id + raw field map). */
fun Map<String, Any?>.toUser(id: String): User = User(
    id = id,
    email = this["email"] as? String ?: "",
    firstName = this["firstName"] as? String ?: "",
    lastName = this["lastName"] as? String ?: "",
    role = (this["role"] as? String ?: "").lowercase(),
    phone = this["phone"] as? String,
    profileImageUrl = this["profileImageUrl"] as? String,
    assignedDoctorId = this["assignedDoctorId"] as? String,
    isBlocked = this["isBlocked"] as? Boolean ?: false,
    apiAccessBlocked = this["apiAccessBlocked"] as? Boolean ?: false,
    blockReason = this["blockReason"] as? String,
    lastSeenMs = (this["lastSeen"] as? Timestamp)?.toDate()?.time,
    hasCompletedOnboarding = this["hasCompletedOnboarding"] as? Boolean ?: false,
    gender = this["gender"] as? String,
    dateOfBirth = this["dateOfBirth"] as? String,
    injuries = (this["injuries"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
    primaryGoal = this["primaryGoal"] as? String,
    speciality = this["speciality"] as? String,
    licenseNumber = this["licenseNumber"] as? String,
    clinicName = this["clinicName"] as? String
)
