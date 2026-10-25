package com.example.update

import com.blinkng.shared.update.BlinkApkResumePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline, server-independent Range validation and resume recovery tests. */
class BlinkApkResumePolicyTest {
    @Test fun acceptsExactStartOffsetAndContentSize() {
        val range = BlinkApkResumePolicy.acceptedRange("bytes 1048576-2097151/5000000", 1048576L, 1048576L)
        assertEquals(1048576L, range?.start)
        assertEquals(2097151L, range?.end)
        assertEquals(5000000L, range?.total)
    }

    @Test fun rejectsMaliciousOrInconsistentContentRanges() {
        val good = "bytes 100-199/200"
        assertNull(BlinkApkResumePolicy.acceptedRange(good, 90L, 100L))
        assertNull(BlinkApkResumePolicy.acceptedRange(good, 100L, 90L))
        assertNull(BlinkApkResumePolicy.acceptedRange(good, 0L, 100L))
        assertNull(BlinkApkResumePolicy.acceptedRange("bytes 100-200/200", 100L, 101L))
        assertNull(BlinkApkResumePolicy.acceptedRange("bytes 200-100/300", 200L, 101L))
        assertNull(BlinkApkResumePolicy.acceptedRange("bytes -1-199/200", 100L, 100L))
        assertNull(BlinkApkResumePolicy.acceptedRange("bytes 100-199/*", 100L, 100L))
        assertNull(BlinkApkResumePolicy.acceptedRange("bytes 100-199/99999999999", 100L, 100L))
        assertNull(BlinkApkResumePolicy.acceptedRange("bytes 999999999999999999999-200/300", 100L, 101L))
    }

    @Test fun ignoredRangeMustRestartInsteadOfAppending() {
        assertTrue(BlinkApkResumePolicy.restartFromZero(200))
        assertFalse(BlinkApkResumePolicy.restartFromZero(206))
        assertFalse(BlinkApkResumePolicy.restartFromZero(416))
        assertFalse(BlinkApkResumePolicy.restartFromZero(404))
    }
}
