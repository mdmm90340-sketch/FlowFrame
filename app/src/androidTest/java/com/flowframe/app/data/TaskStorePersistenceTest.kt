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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskStorePersistenceTest {
    private fun isolatedContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(base.cacheDir, "store-test-${UUID.randomUUID()}").apply { mkdirs() }
        return object : ContextWrapper(base) { override fun getFilesDir(): File = folder }
    }
    private fun task(id: String) = DownloadTask(id, "https://b23.tv/example", Platform.BILIBILI,
        "example", "Test fixture", preset = QualityPreset.RECOMMENDED)

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
