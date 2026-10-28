package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileCompletionPolicyTest {
    private fun completion(type: String, university: String = "", faculty: String = "",
                           department: String = "", level: String = "") =
        ProfileCompletionPolicy.calculate(
            "Ada", "ada", "avatar", "Creator", "Bio", university, faculty, department, level, type
        )

    @Test fun nonStudentsDoNotNeedCampusFields() {
        assertEquals(100, completion("NON_STUDENT"))
    }

    @Test fun studentsHaveOneConsistentCompletionPolicy() {
        assertEquals(55, completion("STUDENT"))
        assertEquals(100, completion("STUDENT", "FUTA", "Science", "Statistics", "200"))
    }

    @Test fun missingAvatarIsReflectedInCompletion() {
        assertEquals(80, ProfileCompletionPolicy.calculate(
            "Ada", "ada", "", "Creator", "Bio", "", "", "", "", "NON_STUDENT"
        ))
    }
}
