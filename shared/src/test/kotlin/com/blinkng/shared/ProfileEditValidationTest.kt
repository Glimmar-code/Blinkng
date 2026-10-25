package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileEditValidationTest {
    private fun validate(
        username: String = "student_21",
        fullName: String = "Test Student",
        email: String = "student@example.edu.ng",
        phone: String = "+2348012345678",
        whatsapp: String = "+2348012345678",
        graduationYear: String = "2028",
        links: List<String> = listOf("https://example.org")
    ) = ProfileEditValidation.validate(fullName, username, email, phone, whatsapp, graduationYear, links)

    @Test fun validCampusProfile() {
        assertNull(validate())
    }

    @Test fun validEmptyOptionalFields() {
        assertNull(validate(email = "", phone = "", whatsapp = "", graduationYear = "", links = emptyList()))
    }

    @Test fun rejectsInvalidUsername() {
        assertEquals("Username must contain 2–30 letters, numbers, dots, dashes or underscores.",
            validate(username = "invalid name"))
    }

    @Test fun rejectsShortOrPlaceholderName() {
        assertEquals("Display name must contain 2–60 characters.", validate(fullName = "A"))
        assertEquals("Display name must contain 2–60 characters.", validate(fullName = "Blink User"))
    }

    @Test fun rejectsInvalidContactDetails() {
        assertEquals("Enter a valid email address.", validate(email = "bad-email"))
        assertEquals("Enter a valid phone number.", validate(phone = "hello"))
        assertEquals("Enter a valid WhatsApp number.", validate(whatsapp = "hello"))
    }

    @Test fun rejectsBadGraduationYear() {
        assertEquals("Graduation year must be a number.", validate(graduationYear = "twenty"))
        assertEquals("Enter a valid graduation year.", validate(graduationYear = "1880"))
    }

    @Test fun disallowsUnsafeExternalLinks() {
        assertEquals("Enter a valid public website or social link.",
            validate(links = listOf("javascript:alert(1)")))
        assertEquals("Enter a valid public website or social link.",
            validate(links = listOf("file:///data/user/0/secret")))
    }
}
