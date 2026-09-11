package com.flowframe.app.worker

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadConcurrencyGateTest {
    @Test
    fun releasingCapacityStartsTheWaitingTaskWithoutAPollingDelay() = runTest {
        val gate = DownloadConcurrencyGate(MutableStateFlow(1)) { it }
        gate.acquire()
        var started = false
        val waiting = launch {
            gate.acquire()
            started = true
            gate.release()
        }
        runCurrent()
        gate.release()
        runCurrent()
        try {
            assertTrue("Freed capacity should immediately wake the waiting task", started)
        } finally {
            waiting.cancelAndJoin()
        }
    }

    @Test
    fun raisingTheLimitStartsWaitingWorkWithoutStoppingTheRunningTask() = runTest {
        val limit = MutableStateFlow(1)
        val gate = DownloadConcurrencyGate(limit) { it }
        gate.acquire()
        var started = false
        val waiting = launch {
            gate.acquire()
            started = true
            gate.release()
        }
        runCurrent()
        assertFalse(started)
        limit.value = 2
        runCurrent()
        assertTrue(started)
        waiting.join()
        gate.release()
    }

    @Test
    fun loweringTheLimitOnlyRestrictsSubsequentStarts() = runTest {
        val limit = MutableStateFlow(3)
        val gate = DownloadConcurrencyGate(limit) { it }
        repeat(3) { gate.acquire() }
        var started = false
        val waiting = launch {
            gate.acquire()
            started = true
            gate.release()
        }
        limit.value = 1
        runCurrent()
        repeat(2) {
            gate.release()
            runCurrent()
            assertFalse(started)
        }
        gate.release()
        runCurrent()
        assertTrue(started)
        waiting.join()
    }

    @Test
    fun cancelingAWaitingTaskDoesNotConsumeFutureCapacity() = runTest {
        val gate = DownloadConcurrencyGate(MutableStateFlow(1)) { it }
        gate.acquire()
        val canceled = launch { gate.acquire(); error("Must remain queued") }
        runCurrent()
        canceled.cancelAndJoin()
        var started = false
        val next = launch { gate.acquire(); started = true; gate.release() }
        runCurrent()
        gate.release()
        runCurrent()
        assertTrue(started)
        next.join()
    }

    @Test
    fun simultaneousWaitersNeverExceedTheClampedLimit() = runTest {
        for ((setting, expectedLimit) in listOf(-1 to 1, 2 to 2, 99 to 3)) {
            val gate = DownloadConcurrencyGate(MutableStateFlow(setting)) { it }
            var active = 0
            var peak = 0
            var finished = 0
            val tasks = List(30) {
                launch {
                    gate.acquire()
                    active++
                    peak = maxOf(peak, active)
                    kotlinx.coroutines.yield()
                    active--
                    gate.release()
                    finished++
                }
            }
            tasks.forEach { it.join() }
            assertEquals(expectedLimit, peak)
            assertEquals(30, finished)
        }
    }
}
