package com.blinkng.shared

/**
 * Profile strength for both saved and edited profile state.
 * Only relevant identity fields count; non-student accounts do not need campus details.
 */
object ProfileCompletionPolicy {
    fun calculate(
        fullName: String,
        username: String,
        avatarUrl: String,
        headline: String,
        bio: String,
        university: String,
        faculty: String,
        department: String,
        academicLevel: String,
        accountType: String
    ): Int {
        val values = mutableListOf(
            fullName.isNotBlank(), username.isNotBlank(), avatarUrl.isNotBlank(),
            headline.isNotBlank(), bio.isNotBlank()
        )
        if (!accountType.equals("NON_STUDENT", ignoreCase = true)) {
            values.addAll(listOf(
                university.isNotBlank(), faculty.isNotBlank(),
                department.isNotBlank(), academicLevel.isNotBlank()
            ))
        }
        return (values.count { it } * 100 / values.size).coerceIn(0, 100)
    }
}
