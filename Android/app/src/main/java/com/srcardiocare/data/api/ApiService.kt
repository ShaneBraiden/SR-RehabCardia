// ApiService.kt - DEPRECATED: Replaced by FirebaseService.kt
// Retrofit REST API interface has been removed.
// All data operations now go through FirebaseService (direct Firebase SDK calls).
//
// Migration mapping:
//   api.login()           -> FirebaseService.login(email, password)
//   api.register()        -> FirebaseService.register(email, password, ...)
//   api.getPatients()     -> FirebaseService.fetchPatients(doctorId)
//   api.getExercises()    -> FirebaseService.fetchExercises(category)
//   api.getPlans()        -> FirebaseService.fetchPlans(patientId)
//   api.startWorkout()    -> FirebaseService.startWorkout(planId, total)
//   api.submitFeedback()  -> FirebaseService.submitFeedback(workoutId, ...)
//   api.getAppointments() -> FirebaseService.fetchAppointments(userId, role)
//   api.uploadVideo()     -> FirebaseService.uploadVideo(accessToken, data, title)
//
// See: FirebaseService.kt (com.srcardiocare.data.firebase)

package com.srcardiocare.data.api
