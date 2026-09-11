package com.flowframe.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.Platform
import com.flowframe.app.core.model.QualityPreset
import com.flowframe.app.core.model.TaskStage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class TaskStorePersistenceTest {
    private fun isolatedContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(base.cacheDir, "store-test-${UUID.randomUUID()}").apply { mkdirs() }
        return object : ContextWrapper(base) { override fun getFilesDir(): File = folder }
    }
    private fun task(id: String) = DownloadTask(id, "https://b23.tv/example", Platform.BILIBILI,
        "example", "Test fixture", preset = QualityPreset.RECOMMENDED, updatedAtEpochMillis = 1L)

    @Test fun unchangedUpdateDoesNotWriteOrReplaceTheRecord() = runBlocking {
        val context = isolatedContext()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = TaskStore(context, scope) { 10_000L }
            store.add(task("unchanged"))
            val before = store.find("unchanged")!!
            val file = File(context.filesDir, "download_tasks.json")
            val originalBytes = file.readBytes()
            store.update("unchanged") { it.copy(progress = 0f) }
            assertArrayEquals(originalBytes, file.readBytes())
            assertSame(before, store.find("unchanged"))
        } finally { scope.cancel() }
    }

    @Test fun removingMissingRecordDoesNotRewriteHistory() = runBlocking {
        val context = isolatedContext()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = TaskStore(context, scope) { 10_000L }
            store.add(task("retained"))
            val file = File(context.filesDir, "download_tasks.json")
            assertTrue(file.setLastModified(1_000_000L))
            val originalModified = file.lastModified()
            val before = store.find("retained")!!
            store.remove("absent")
            assertEquals(originalModified, file.lastModified())
            assertSame(before, store.find("retained"))
        } finally { scope.cancel() }
    }

    @Test fun idleStoreHasNoScheduledPersistenceWork() = runBlocking {
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.IO)
        try {
            val store = TaskStore(isolatedContext(), scope) { 10_000L }
            store.awaitLoaded()
            // The load coroutine may still be returning after completing awaitLoaded.
            repeat(20) { if (parent.children.any()) delay(10) }
            assertTrue("An idle store must not retain a periodic flush coroutine", parent.children.none())
        } finally { scope.cancel() }
    }

    private suspend fun awaitIdle(parent: Job) = withTimeout(5_000) {
        while (parent.children.any()) delay(10)
    }

    @Test fun deferredUpdatesSaveTheLatestSnapshotAndKeepUnchangedRecords() = runBlocking {
        val context = isolatedContext()
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.IO)
        val clock = AtomicLong(10_000L)
        try {
            val store = TaskStore(context, scope, clock::get)
            val unchanged = task("unchanged")
            store.add(unchanged)
            store.add(task("progress").copy(thumbnailUrl = "https://example.invalid/ephemeral"))
            awaitIdle(parent)
            assertSame(unchanged, store.find("unchanged"))
            assertNull(store.find("progress")!!.thumbnailUrl)
            val file = File(context.filesDir, "download_tasks.json")
            val originalBytes = file.readBytes()
            store.update("progress", persistImmediately = false) { it.copy(progress = .25f) }
            clock.set(10_500L)
            store.update("progress", persistImmediately = false) { it.copy(progress = .5f) }
            assertArrayEquals(originalBytes, file.readBytes())
            assertEquals(1, parent.children.count())
            assertSame(unchanged, store.find("unchanged"))
            awaitIdle(parent)
            assertSame(unchanged, store.find("unchanged"))
            val reopened = TaskStore(context, scope, clock::get)
            reopened.awaitLoaded()
            assertEquals(.5f, reopened.find("progress")!!.progress, 0f)
            assertNull(reopened.find("progress")!!.thumbnailUrl)
        } finally { scope.cancel() }
    }

    @Test fun terminalUpdateBypassesTheDeferredWindowAndCancelsPendingWrite() = runBlocking {
        val context = isolatedContext()
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.IO)
        try {
            val store = TaskStore(context, scope) { 10_000L }
            store.add(task("finishing"))
            awaitIdle(parent)
            store.update("finishing", persistImmediately = false) { it.copy(progress = .5f) }
            store.update("finishing", persistImmediately = false) {
                it.copy(stage = TaskStage.COMPLETED, progress = 1f,
                    outputLocations = listOf("content://media/video/84"))
            }
            val reopened = TaskStore(context, scope) { 10_000L }
            reopened.awaitLoaded()
            assertEquals(TaskStage.COMPLETED, reopened.find("finishing")!!.stage)
            assertEquals(listOf("content://media/video/84"), reopened.find("finishing")!!.resolvedOutputLocations)
            withTimeout(500) { awaitIdle(parent) }
        } finally { scope.cancel() }
    }

    @Test fun failedDeferredWriteRetainsProgressAndRecoversAfterStorageIsWritable() = runBlocking {
        val context = isolatedContext()
        val directory = context.filesDir
        val backup = File(directory.parentFile, "${directory.name}-blocked")
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.IO)
        try {
            val store = TaskStore(context, scope) { 10_000L }
            store.add(task("recovering"))
            awaitIdle(parent)
            store.update("recovering", persistImmediately = false) { it.copy(progress = .6f) }
            val firstFlush = parent.children.single()
            // Preserve the history directory while making the original parent path unwritable.
            assertTrue(directory.renameTo(backup))
            directory.writeText("temporarily blocked", Charsets.UTF_8)
            // Wait for the actual blocked write attempt, not an assumed wall-clock schedule.
            withTimeout(5_000) { firstFlush.join() }
            assertEquals(.6f, store.find("recovering")!!.progress, 0f)
            assertEquals("Only one delayed retry may be active", 1, parent.children.count())
            assertTrue(directory.delete())
            assertTrue(backup.renameTo(directory))
            awaitIdle(parent)
            val reopened = TaskStore(context, scope) { 10_000L }
            reopened.awaitLoaded()
            assertEquals(.6f, reopened.find("recovering")!!.progress, 0f)
        } finally {
            scope.cancel()
            if (backup.exists()) {
                if (directory.isFile) directory.delete()
                backup.renameTo(directory)
            }
        }
    }

    @Test fun canceledRecordSurvivesLateDownloadCallbackAndReload() = runBlocking {
        val context = isolatedContext()
        val store = TaskStore(context)
        store.add(task("canceled"))
        store.update("canceled") { it.copy(stage = TaskStage.CANCELED) }
        store.update("canceled") { it.copy(stage = TaskStage.DOWNLOADING, progress = .9f) }
        assertEquals(TaskStage.CANCELED, store.find("canceled")!!.stage)
        val reopened = TaskStore(context)
        reopened.add(task("new"))
        assertEquals(TaskStage.CANCELED, reopened.find("canceled")!!.stage)
    }

    @Test fun completedMediaSurvivesLateCancellationAndRecordRemoval() = runBlocking {
        val context = isolatedContext()
        val store = TaskStore(context)
        store.add(task("done"))
        store.update("done") { it.copy(stage = TaskStage.COMPLETED, outputLocations = listOf("content://media/video/42")) }
        store.update("done") { it.copy(stage = TaskStage.CANCELED) }
        assertEquals(TaskStage.COMPLETED, store.find("done")!!.stage)
        assertEquals(listOf("content://media/video/42"), store.find("done")!!.resolvedOutputLocations)
        store.remove("done")
        val reopened = TaskStore(context)
        reopened.add(task("new"))
        assertEquals(listOf("new"), reopened.tasks.value.map { it.id })
    }
}
