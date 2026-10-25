package com.example.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class PerKeyMutationLocks {
    private data class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val guard = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    suspend fun <T> withKey(key: String, action: suspend () -> T): T {
        val lease = guard.withLock {
            entries.getOrPut(key) { Entry() }.also { it.users += 1 }
        }
        try {
            return lease.mutex.withLock { action() }
        } finally {
            withContext(NonCancellable) {
                guard.withLock {
                    lease.users -= 1
                    if (lease.users == 0 && entries[key] === lease) entries.remove(key)
                }
            }
        }
    }

    suspend fun retainedKeyCountForTest(): Int = guard.withLock { entries.size }
}
