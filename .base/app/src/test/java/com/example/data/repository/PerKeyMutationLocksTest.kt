package com.example.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PerKeyMutationLocksTest {
    @Test fun releasesKeysAfterOperations() = runBlocking {
        val locks = PerKeyMutationLocks()
        repeat(300) { i -> locks.withKey("post-$i") { i } }
        assertEquals(0, locks.retainedKeyCountForTest())
    }

    @Test fun serializesWaitersForSamePost() = runBlocking {
        val locks = PerKeyMutationLocks()
        var simultaneouslyInside = 0
        var peak = 0
        val tasks = (1..12).map {
            async {
                locks.withKey("same-post") {
                    simultaneouslyInside += 1
                    peak = maxOf(peak, simultaneouslyInside)
                    delay(2)
                    simultaneouslyInside -= 1
                }
            }
        }
        tasks.forEach { it.await() }
        assertEquals(1, peak)
        assertEquals(0, locks.retainedKeyCountForTest())
    }
}
