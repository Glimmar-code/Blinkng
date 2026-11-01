package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkPostTagPresentationTest {
    @Test fun avoidsDuplicatingHashtagsAlreadyPresentInCaption() {
        assertEquals(
            listOf("#SchoolLife", "#BLINK"),
            BlinkPostTagPresentation.extraTags(
                "Another beautiful day at FUTA! #FUTA #CampusLife",
                listOf("#FUTA", "CampusLife", "SchoolLife", "#BLINK")
            )
        )
    }

    @Test fun deduplicatesCaseInsensitivelyWithoutMutatingCaption() {
        assertEquals(
            listOf("#Stats", "#Life"),
            BlinkPostTagPresentation.extraTags(
                "Welcome to #FUTA today",
                listOf("futa", "Stats", "#stats", "Life")
            )
        )
    }

    @Test fun honorsExactTagBoundariesAndRejectsInvalidTokens() {
        assertEquals(
            listOf("#FUTA", "#Valid_tag"),
            BlinkPostTagPresentation.extraTags(
                "This is #FUTALife but not FUTA",
                listOf("FUTA", "bad tag", "Valid_tag", "")
            )
        )
    }
}
