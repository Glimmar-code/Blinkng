package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileEditorValidationRegressionTest {
    private fun validate(
        name: String = "Campus Student",
        handle: String = "student_21",
        email: String = "student@example.edu.ng",
        phone: String = "+2348012345678",
        whatsapp: String = "+2348012345678",
        year: String = "2028",
        links: List<String> = listOf("https://example.org")
    ): String? = ProfileEditValidation.validate(name, handle, email, phone, whatsapp, year, links)

    @Test fun acceptsValidStudent() {
        assertNull(validate())
    }

    @Test fun preventsInvalidHandleOrName() {
        assertEquals("Username must contain 2–30 letters, numbers, dots, dashes or underscores.",
            validate(handle = "space is invalid"))
        assertEquals("Display name must contain 2–60 characters.", validate(name = "A"))
    }

    @Test fun rejectsInvalidContactAndYear() {
        assertEquals("Enter a valid email address.", validate(email = "broken"))
        assertEquals("Enter a valid phone number.", validate(phone = "123"))
        assertEquals("Graduation year must be a number.", validate(year = "future"))
    }

    @Test fun blocksUnsafeProfileLinks() {
        assertEquals("Enter a valid public website or social link.",
            validate(links = listOf("javascript:alert(1)")))
    }
}
