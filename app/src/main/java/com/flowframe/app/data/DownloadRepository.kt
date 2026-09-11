package com.flowframe.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.await
import com.flowframe.app.core.engine.DownloadEngine
import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.GalleryOutputMode
import com.flowframe.app.core.model.MediaKind
import com.flowframe.app.core.model.MediaPreview
import com.flowframe.app.core.model.QualityPreset
import com.flowframe.app.core.model.TaskStage
import com.flowframe.app.core.url.SupportedUrlParser
import com.flowframe.app.worker.DownloadWorker
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class DownloadRepository(
    context: Context,
    private val store: TaskStore,
    private val engine: DownloadEngine,
    private val engineReady: Deferred<Unit>,
    private val settingsStore: AppSettingsStore,
) {
    private val workManager = WorkManager.getInstance(context)
    private val retryMutex = Mutex()

    val tasks: StateFlow<List<DownloadTask>> = store.tasks

    suspend fun parse(rawText: String, processId: String = PARSE_PROCESS_ID): MediaPreview {
        val supported = SupportedUrlParser.extract(rawText)
            ?: throw IllegalArgumentException("没有找到支持的作品链接")
        engineReady.await()
        return withContext(Dispatchers.IO) {
            engine.parse(supported.value, supported.platform, processId)
        }
    }

    fun cancelProcess(processId: String) {
        engine.cancel(processId)
    }

    suspend fun enqueue(
        preview: MediaPreview,
        preset: QualityPreset,
        galleryOutputMode: GalleryOutputMode? = null,
        selectedImageIndices: List<Int>? = null,
        outputDirectoryUri: String? = settingsStore.state.value.outputDirectoryUri,
    ): DownloadTask {
        val uuid = UUID.randomUUID()
        if (preview.mediaKind == MediaKind.GALLERY && galleryOutputMode != GalleryOutputMode.AUDIO) {
            require(selectedImageIndices == null || selectedImageIndices.isNotEmpty()) { "请至少选择一张图片" }
        }
        val task = DownloadTask(
            id = uuid.toString(),
            sourceUrl = preview.sourceUrl,
            platform = preview.platform,
            mediaId = preview.mediaId,
            title = preview.title,
            uploader = preview.uploader,
            thumbnailUrl = preview.thumbnailUrl,
            durationSeconds = preview.durationSeconds,
            selectedImageIndices = selectedImageIndices?.distinct()?.sorted(),
            outputDirectoryUri = outputDirectoryUri,
            preset = preset,
            mediaKind = preview.mediaKind,
            galleryOutputMode = if (preview.mediaKind == MediaKind.GALLERY) {
                galleryOutputMode ?: GalleryOutputMode.MP4
            } else {
                null
            },
        )
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(uuid)
            .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to task.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        commitQueuedTask(
            persist = { store.add(task) },
            schedule = {
                workManager.enqueueUniqueWork(
                    "flowframe-download-${task.id}", ExistingWorkPolicy.KEEP, request,
                ).await()
            },
            rollback = { store.remove(task.id) },
        )
        return task
    }

    suspend fun retry(taskId: String): Unit = withContext(NonCancellable) {
        retryMutex.withLock {
            store.awaitLoaded()
            val old = store.find(taskId) ?: return@withLock
            if (old.stage != TaskStage.FAILED && old.stage != TaskStage.CANCELED) return@withLock
            enqueue(
                preview = MediaPreview(
                    sourceUrl = old.sourceUrl,
                    platform = old.platform,
                    mediaId = old.mediaId,
                    title = old.title,
                    uploader = old.uploader,
                    thumbnailUrl = old.thumbnailUrl,
                    durationSeconds = old.durationSeconds,
                    mediaKind = old.mediaKind,
                    imageCount = if (old.mediaKind == MediaKind.GALLERY) old.outputLocations.size else 0,
                    hasAudio = old.mediaKind == MediaKind.GALLERY &&
                        old.galleryOutputMode != GalleryOutputMode.IMAGES,
                ),
                preset = old.preset,
                galleryOutputMode = old.galleryOutputMode,
                selectedImageIndices = old.selectedImageIndices,
                outputDirectoryUri = settingsStore.state.value.outputDirectoryUri,
            )
            store.remove(taskId)
        }
    }

    suspend fun cancel(taskId: String) {
        store.awaitLoaded()
        store.update(taskId) { it.copy(stage = TaskStage.CANCELED, etaSeconds = null) }
        runCatching { workManager.cancelWorkById(UUID.fromString(taskId)) }
        engine.cancel(taskId)
    }

    suspend fun remove(taskId: String) {
        store.awaitLoaded()
        val task = store.find(taskId) ?: return
        if (task.stage !in setOf(TaskStage.COMPLETED, TaskStage.FAILED, TaskStage.CANCELED)) return
        runCatching { workManager.cancelWorkById(UUID.fromString(taskId)) }
        store.remove(taskId)
    }

    companion object {
        const val PARSE_PROCESS_ID = "flowframe-preview-parse"
    }
}
