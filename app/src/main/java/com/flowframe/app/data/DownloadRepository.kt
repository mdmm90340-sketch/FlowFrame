package com.flowframe.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
import kotlinx.coroutines.flow.StateFlow
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

    val tasks: StateFlow<List<DownloadTask>> = store.tasks

    suspend fun parse(rawText: String, processId: String = PARSE_PROCESS_ID): MediaPreview {
        val supported = SupportedUrlParser.extract(rawText)
            ?: throw IllegalArgumentException("没有找到可识别的抖音或 B站链接")
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
    ): DownloadTask {
        val uuid = UUID.randomUUID()
        val task = DownloadTask(
            id = uuid.toString(),
            sourceUrl = preview.sourceUrl,
            platform = preview.platform,
            mediaId = preview.mediaId,
            title = preview.title,
            uploader = preview.uploader,
            thumbnailUrl = preview.thumbnailUrl,
            durationSeconds = preview.durationSeconds,
            preset = preset,
            mediaKind = preview.mediaKind,
            galleryOutputMode = if (preview.mediaKind == MediaKind.GALLERY) {
                galleryOutputMode ?: GalleryOutputMode.MP4
            } else {
                null
            },
        )
        store.add(task)

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(uuid)
            .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to task.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (settingsStore.state.value.wifiOnly) {
                            NetworkType.UNMETERED
                        } else {
                            NetworkType.CONNECTED
                        },
                    )
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            "flowframe-download-${task.id}",
            ExistingWorkPolicy.KEEP,
            request,
        )
        return task
    }

    suspend fun retry(taskId: String) {
        val old = store.find(taskId) ?: return
        workManager.cancelWorkById(UUID.fromString(taskId))
        store.remove(taskId)
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
        )
    }

    suspend fun cancel(taskId: String) {
        runCatching { workManager.cancelWorkById(UUID.fromString(taskId)) }
        engine.cancel(taskId)
        store.update(taskId) { it.copy(stage = TaskStage.CANCELED, etaSeconds = null) }
    }

    suspend fun remove(taskId: String) {
        runCatching { workManager.cancelWorkById(UUID.fromString(taskId)) }
        store.remove(taskId)
    }

    companion object {
        const val PARSE_PROCESS_ID = "flowframe-preview-parse"
    }
}
