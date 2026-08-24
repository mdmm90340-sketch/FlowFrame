package com.flowframe.app.worker

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadConcurrencyGate(
    private val currentLimit: () -> Int,
) {
    private val mutex = Mutex()
    private var activeDownloads = 0

    suspend fun acquire() {
        while (true) {
            val acquired = mutex.withLock {
                if (activeDownloads < currentLimit().coerceIn(1, 3)) {
                    activeDownloads += 1
                    true
                } else {
                    false
                }
            }
            if (acquired) return
            delay(250)
        }
    }

    suspend fun release() {
        mutex.withLock {
            activeDownloads = (activeDownloads - 1).coerceAtLeast(0)
        }
    }
}

