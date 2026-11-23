package com.blinkng.desktop.shared

import com.blinkng.shared.BlinkOnboardingPolicy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

/** Desktop's regular JVM test task exercises the shared Android/Windows policy. */
class BlinkOnboardingBasicsTest {
    private fun validation(
        university: String = "University",
        department: String = "Computing",
        gender: String = "Prefer not to say",
        birthday: String = "2001-02-03",
        phone: String = "",
        notStudent: Boolean = false,
    ) = BlinkOnboardingPolicy.basicsValidationMessage(
        university, department, gender, birthday, phone, notStudent
    )

    @Test fun studentNeedsUniversityAndDepartment() {
        assertNotNull(validation(university = ""))
        assertNotNull(validation(department = ""))
        assertNull(validation())
    }

    @Test fun nonStudentDoesNotNeedCampusDetails() {
        assertNull(validation(university = "", department = "", notStudent = true))
    }

    @Test fun genderAndBirthdayRemainRequiredForNonStudents() {
        assertNotNull(validation(gender = "", notStudent = true))
        assertNotNull(validation(birthday = "", notStudent = true))
    }

    @Test fun invalidFutureAndImpossibleBirthdaysAreRejected() {
        for (birthday in listOf("", "2099-01-01", "2001-02-29", "2001-13-03", "2001-2-3")) {
            assertNotNull(validation(birthday = birthday))
        }
        assertNull(validation(birthday = "2000-02-29"))
    }

    @Test fun phoneIsOptionalButInvalidNumbersAreNotSaved() {
        assertNull(validation(phone = ""))
        assertNull(validation(phone = "08012345678"))
        assertNull(validation(phone = "+2348012345678"))
        assertNotNull(validation(phone = "abc"))
        assertNotNull(validation(phone = "123"))
    }
}
