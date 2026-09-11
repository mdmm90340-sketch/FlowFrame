package com.flowframe.app.worker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadConcurrencyGate<S>(
    private val settings: StateFlow<S>,
    private val limit: (S) -> Int,
) {
    private val mutex = Mutex()
    private val activeDownloads = MutableStateFlow(0)
    private val capacityAvailable = combine(activeDownloads, settings) { active, value ->
        active < limit(value).coerceIn(1, 3)
    }

    suspend fun acquire() {
        while (true) {
            // Suspend until a running task exits or the user changes the limit.
            capacityAvailable.first { it }
            val acquired = mutex.withLock {
                // Several waiters can wake together; claim capacity under the lock.
                // Read the original settings, not an asynchronously cached projection.
                if (activeDownloads.value < limit(settings.value).coerceIn(1, 3)) {
                    activeDownloads.value += 1
                    true
                } else {
                    false
                }
            }
            if (acquired) return
        }
    }

    suspend fun release() {
        mutex.withLock {
            activeDownloads.value = (activeDownloads.value - 1).coerceAtLeast(0)
        }
    }
}
