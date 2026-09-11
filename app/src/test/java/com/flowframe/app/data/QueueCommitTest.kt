package com.flowframe.app.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueCommitTest {
    @Test
    fun leavingAfterPersistenceStillSchedulesTheAcceptedTask() = runTest {
        val persisted = CompletableDeferred<Unit>()
        val returnFromPersistence = CompletableDeferred<Unit>()
        var recordExists = false
        var scheduled = false
        var rolledBack = false

        val caller = launch {
            commitQueuedTask(
                persist = {
                    recordExists = true
                    persisted.complete(Unit)
                    returnFromPersistence.await()
                },
                schedule = { scheduled = true },
                rollback = { recordExists = false; rolledBack = true },
            )
        }
        persisted.await()
        caller.cancel()
        returnFromPersistence.complete(Unit)
        caller.join()

        assertTrue("A persisted task must have scheduled work after the caller leaves", scheduled)
        assertTrue("A successfully scheduled task retains its record", recordExists)
        assertFalse(rolledBack)
    }

    @Test
    fun failedSchedulingRollsBackEvenWhenTheCallerLeaves() = runTest {
        val scheduling = CompletableDeferred<Unit>()
        val failScheduling = CompletableDeferred<Unit>()
        val schedulerFailure = IllegalStateException("Scheduler rejected the task")
        var recordExists = false
        var rollbackCount = 0
        var observedFailure: Throwable? = null

        val caller = launch {
            try {
                commitQueuedTask(
                    persist = { recordExists = true },
                    schedule = {
                        scheduling.complete(Unit)
                        failScheduling.await()
                        throw schedulerFailure
                    },
                    rollback = {
                        yield()
                        recordExists = false
                        rollbackCount++
                    },
                )
            } catch (error: Throwable) {
                observedFailure = error
            }
        }
        scheduling.await()
        caller.cancel()
        failScheduling.complete(Unit)
        caller.join()

        assertFalse("Failed scheduling must not leave an orphan queue record", recordExists)
        assertEquals(1, rollbackCount)
        assertTrue("Preserve the scheduler failure type after compensation", observedFailure is IllegalStateException)
        assertEquals("Scheduler rejected the task", observedFailure?.message)
    }
}
