package com.blinkng.shared.discovery

/** Account type is self-declared. It never grants an official university badge. */
enum class BlinkCampusAccountType(val label: String) {
    STUDENT("Student"),
    ASPIRANT("Aspirant"),
    GRADUATE("Graduate"),
    STAFF("Staff");

    companion object {
        fun fromWire(value: String?): BlinkCampusAccountType? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

/** Shared validation contract for registration, edit profile and campus ranking. */
object BlinkCampusRegistrationPolicy {
    val academicLevels = listOf(
        "100 Level", "200 Level", "300 Level", "400 Level",
        "500 Level", "600 Level", "700 Level", "Postgraduate",
    )

    fun requiresAcademicDetails(accountType: String?): Boolean =
        BlinkCampusAccountType.fromWire(accountType) == BlinkCampusAccountType.STUDENT

    fun canCompleteCampus(
        university: String,
        accountType: String?,
        faculty: String,
        department: String,
        level: String,
    ): Boolean {
        if (university.isBlank() || BlinkCampusAccountType.fromWire(accountType) == null) return false
        if (requiresAcademicDetails(accountType)) {
            return faculty.isNotBlank() && department.isNotBlank() && level in academicLevels
        }
        return true
    }

    fun canJoinCampusRanking(university: String, knownUniversities: Collection<String>): Boolean =
        knownUniversities.any { it.equals(university.trim(), ignoreCase = true) }
}
