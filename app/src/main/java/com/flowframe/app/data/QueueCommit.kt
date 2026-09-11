package com.flowframe.app.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Commits a queue record and its scheduler entry together, compensating a failed submission.
 * This short local transaction outlives its caller; no media or network work belongs here.
 */
internal suspend fun <T> commitQueuedTask(
    persist: suspend () -> Unit,
    schedule: suspend () -> T,
    rollback: suspend () -> Unit,
): T = withContext(NonCancellable) {
    persist()
    try {
        schedule()
    } catch (error: Throwable) {
        try {
            rollback()
        } catch (cleanupError: Throwable) {
            if (cleanupError !== error) error.addSuppressed(cleanupError)
        }
        throw error
    }
}
