package com.flowframe.app

import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.Platform
import com.flowframe.app.core.model.QualityPreset
import com.flowframe.app.core.model.TaskStage
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Counts observable work in the real VM, using only records created by this test. */
@RunWith(AndroidJUnit4::class)
class MainViewModelProjectionTest {
    @Test
    fun progressPreservesDraftWritesAndUnchangedRows(): Unit = runBlocking {
        withTimeout(45_000L) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val app = instrumentation.targetContext.applicationContext as FlowFrameApplication
            val taskStore = app.container.taskStore
            val movingId = UUID.randomUUID().toString()
            val unchangedId = UUID.randomUUID().toString()
            val viewModelStore = ViewModelStore()
            val savedState = SavedStateHandle(mapOf("input" to "controlled draft"))
            val inputWrites = AtomicInteger()
            val observer = Observer<String> { inputWrites.incrementAndGet() }
            lateinit var viewModel: MainViewModel
            try {
                taskStore.awaitLoaded()
                taskStore.add(fixture(movingId))
                taskStore.add(fixture(unchangedId))
                withContext(Dispatchers.Main) {
                    viewModel = MainViewModel(app, savedState)
                    viewModelStore.put("projection-test", viewModel)
                    savedState.getLiveData<String>("input").observeForever(observer)
                }
                viewModel.uiState.first { state ->
                    state.tasks.items.any { it.id == movingId } &&
                        state.tasks.items.any { it.id == unchangedId }
                }
                instrumentation.waitForIdleSync()
                inputWrites.set(0)
                var previousRow = viewModel.uiState.value.tasks.items.first { it.id == unchangedId }
                var rebuiltRows = 0
                repeat(5) { step ->
                    val progress = (step + 2) / 10f
                    taskStore.update(movingId, persistImmediately = false) {
                        it.copy(progress = progress, downloadedBytes = (step + 2) * 1_024L)
                    }
                    viewModel.uiState.first { state ->
                        state.tasks.items.firstOrNull { it.id == movingId }?.progress == progress
                    }
                    instrumentation.waitForIdleSync()
                    val currentRow = viewModel.uiState.value.tasks.items.first { it.id == unchangedId }
                    if (currentRow !== previousRow) rebuiltRows++
                    previousRow = currentRow
                }
                val measurements = "progress_updates=5 draft_input_writes=${inputWrites.get()} " +
                    "unchanged_rows_rebuilt=$rebuiltRows"
                instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$measurements\n") })
                assertEquals(measurements, 0, inputWrites.get())
                assertEquals(measurements, 0, rebuiltRows)
                assertEquals("controlled draft", savedState.get<String>("input"))
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    savedState.getLiveData<String>("input").removeObserver(observer)
                    viewModelStore.clear()
                }
                withContext(NonCancellable) {
                    taskStore.remove(movingId)
                    taskStore.remove(unchangedId)
                }
            }
        }
    }

    private fun fixture(id: String) = DownloadTask(
        id = id,
        sourceUrl = "https://example.invalid/generated-projection-test",
        platform = Platform.DOUYIN,
        mediaId = id,
        title = "Generated projection fixture",
        preset = QualityPreset.RECOMMENDED,
        stage = TaskStage.DOWNLOADING,
        progress = 0.1f,
    )
}
