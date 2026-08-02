// PatientSummary.kt — Derived at-a-glance patient facts for clinician lists.
package com.srcardiocare.ui.screens.doctor

import com.srcardiocare.data.model.Assignment
import com.srcardiocare.data.model.User
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Date shapes seen in `dateOfBirth`. The field is written by more than one
 * route — onboarding, admin entry, seeded data — so parsing accepts each rather
 * than dropping the age of every patient whose record predates a convention.
 */
private val DOB_FORMATS = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    DateTimeFormatter.ofPattern("MM/dd/yyyy")
)

/** Age in whole years, or null if [dateOfBirth] is absent or unparseable. */
fun ageFrom(dateOfBirth: String?, today: LocalDate = LocalDate.now()): Int? {
    val raw = dateOfBirth?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val dob = DOB_FORMATS.firstNotNullOfOrNull { fmt ->
        runCatching { LocalDate.parse(raw, fmt) }.getOrNull()
    } ?: return null
    if (dob.isAfter(today)) return null
    return Period.between(dob, today).years.takeIf { it in 0..130 }
}

/** "M" / "F" / first initial of anything else. Null when unrecorded. */
fun sexInitial(gender: String?): String? =
    gender?.trim()?.takeIf { it.isNotBlank() }?.first()?.uppercase()

/**
 * Which week of rehab the patient is in, counting from their earliest
 * assignment start date. The first seven days are week 1, so the number a
 * clinician reads matches how they speak about it — nobody is in "week 0".
 *
 * Returns null when the patient has never been prescribed anything, which is a
 * different statement from "week 1" and is rendered differently.
 */
fun rehabWeek(assignments: List<Assignment>, today: LocalDate = LocalDate.now()): Int? {
    val start = assignments
        .mapNotNull { runCatching { LocalDate.parse(it.startDate) }.getOrNull() }
        .minOrNull() ?: return null
    if (start.isAfter(today)) return 1
    return (ChronoUnit.DAYS.between(start, today) / 7).toInt() + 1
}

/**
 * The `62 • M • Week 3` line under a patient's name.
 *
 * Every part is optional and simply drops out when unknown, so a sparse record
 * degrades to a shorter line rather than to placeholder noise like "? • ?".
 */
fun patientMetaLine(age: Int?, sex: String?, weekNumber: Int?): String =
    listOfNotNull(
        age?.let { "$it" },
        sex,
        weekNumber?.let { "Week $it" }
    ).joinToString(" • ")

/** Convenience: build the meta line straight off a [User] and their assignments. */
fun patientMetaLine(user: User, assignments: List<Assignment>): String =
    patientMetaLine(
        age = ageFrom(user.dateOfBirth),
        sex = sexInitial(user.gender),
        weekNumber = rehabWeek(assignments)
    )
