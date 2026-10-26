package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkNonStudentOnboardingPolicyTest {
    @Test fun optionalCampusFieldsForNonStudents() {
        assertFalse(BlinkOnboardingPolicy.campusRequired(BlinkOnboardingPolicy.NON_STUDENT))
        assertTrue(BlinkOnboardingPolicy.campusRequired("STUDENT"))
        assertTrue(BlinkOnboardingPolicy.campusRequired(null))
    }
}
