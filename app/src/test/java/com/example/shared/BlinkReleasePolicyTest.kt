package com.example.shared

import com.blinkng.shared.update.BlinkReleasePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkReleasePolicyTest {
    private val good = """
        applicationId=com.aistudio.blink.appvtwo
        versionCode=202610170
        versionName=1.2.0
        commit=${"a".repeat(40)}
        productionSource=main
        signingCertificateSha1=${"c".repeat(40)}
        signingCertificateSha256=${"b".repeat(64)}
        apkSha256=${"c".repeat(64)}
    """.trimIndent()

    @Test fun acceptsVerifiedPublisherFormatOnly() {
        val parsed = BlinkReleasePolicy.parseVerifiedPublisherMetadata(good)
        assertNotNull(parsed)
        assertEquals("1.2.0", parsed?.versionName)
        assertEquals(202610170, parsed?.versionCode)
    }

    @Test fun comparesVersionCodesNotHumanFriendlyNames() {
        val release = BlinkReleasePolicy.parseVerifiedPublisherMetadata(good)
        assertNotNull(BlinkReleasePolicy.availableUpdate(release, 202610169))
        assertNull(BlinkReleasePolicy.availableUpdate(release, 202610170))
        assertNull(BlinkReleasePolicy.availableUpdate(release, 202610171))
    }

    @Test fun rejectsMalformedUnsafeAndIncompleteMetadata() {
        assertNull(BlinkReleasePolicy.parseVerifiedPublisherMetadata(good.replace("productionSource=main", "productionSource=Testlab")))
        assertNull(BlinkReleasePolicy.parseVerifiedPublisherMetadata(good.replace("applicationId=com.aistudio.blink.appvtwo", "applicationId=com.fake.app")))
        assertNull(BlinkReleasePolicy.parseVerifiedPublisherMetadata(good.replace("versionCode=202610170", "versionCode=0")))
        assertNull(BlinkReleasePolicy.parseVerifiedPublisherMetadata(good.replace("apkSha256=${"c".repeat(64)}", "apkSha256=bad")))
        assertNull(BlinkReleasePolicy.parseVerifiedPublisherMetadata(good + "\nversionCode=400"))
        assertNull(BlinkReleasePolicy.parseVerifiedPublisherMetadata(good.repeat(100)))
    }

    @Test fun doesNotMistakeUnavailableReleaseForMandatoryUpdate() {
        assertNull(BlinkReleasePolicy.availableUpdate(null, 101))
    }
}
