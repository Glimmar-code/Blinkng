package com.example.shared

import com.blinkng.shared.discovery.BlinkCampusRegistrationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkCampusRegistrationPolicyTest {
    @Test fun mandatoryCampusAndTypePreventSilentDefaults() {
        assertFalse(BlinkCampusRegistrationPolicy.canCompleteCampus("", "STUDENT", "Engineering", "Physics", "100 Level"))
        assertFalse(BlinkCampusRegistrationPolicy.canCompleteCampus("University of Lagos", "", "", "", ""))
        assertFalse(BlinkCampusRegistrationPolicy.canCompleteCampus("University of Lagos", "STUDENT", "", "Physics", "100 Level"))
        assertFalse(BlinkCampusRegistrationPolicy.canCompleteCampus("University of Lagos", "STUDENT", "Science", "Physics", ""))
        assertTrue(BlinkCampusRegistrationPolicy.canCompleteCampus("University of Lagos", "STUDENT", "Science", "Physics", "100 Level"))
        assertTrue(BlinkCampusRegistrationPolicy.canCompleteCampus("University of Lagos", "ASPIRANT", "", "", ""))
        assertTrue(BlinkCampusRegistrationPolicy.canCompleteCampus("University of Lagos", "STAFF", "", "", ""))
    }

    @Test fun campusRankRequiresRecognizedSelection() {
        val known = listOf("Federal University of Technology, Akure", "University of Lagos")
        assertFalse(BlinkCampusRegistrationPolicy.canJoinCampusRanking("", known))
        assertFalse(BlinkCampusRegistrationPolicy.canJoinCampusRanking("Madeup University", known))
        assertTrue(BlinkCampusRegistrationPolicy.canJoinCampusRanking("university of lagos", known))
    }
}
