package com.flowframe.app.ui.model

import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.GalleryOutputMode
import com.flowframe.app.core.model.MediaKind
import com.flowframe.app.core.model.Platform
import com.flowframe.app.core.model.QualityPreset
import com.flowframe.app.core.model.TaskStage

/** Only network values that can change a queued task's presentation. */
internal data class TaskNetworkConstraints(
    val connected: Boolean,
    val wifi: Boolean,
    val wifiOnly: Boolean,
)

/**
 * Projects task snapshots without reading a repository, settings store, or preview cache.
 * Confine an instance to one collector; unchanged rows retain their UI object and removed
 * records leave the cache. This keeps a progress tick from rebuilding the whole history.
 */
internal class TaskUiProjector {
    private data class CachedTask(
        val source: DownloadTask,
        val thumbnail: String?,
        val reason: String?,
        val row: DownloadTaskUi,
    )

    private val cache = HashMap<String, CachedTask>()
    private var previousRows: List<DownloadTaskUi> = emptyList()

    fun project(
        tasks: List<DownloadTask>,
        network: TaskNetworkConstraints,
        thumbnails: Map<String, String>,
    ): List<DownloadTaskUi> {
        if (cache.size != tasks.size || tasks.any { it.id !in cache }) {
            cache.keys.retainAll(tasks.mapTo(HashSet(tasks.size)) { it.id })
        }
        var unchanged = tasks.size == previousRows.size
        val rows = ArrayList<DownloadTaskUi>(tasks.size)
        tasks.forEachIndexed { index, task ->
            val thumbnail = thumbnails["${task.platform.name}:${task.mediaId}"]
            val reason = when {
                task.stage == TaskStage.CANCELED -> "任务已取消"
                task.stage == TaskStage.QUEUED && !network.connected -> "等待网络恢复"
                task.stage == TaskStage.QUEUED && network.wifiOnly && !network.wifi -> "等待 Wi-Fi 网络"
                else -> task.errorMessage
            }
            val cached = cache[task.id]
            val row = if (cached != null && cached.source === task && cached.thumbnail == thumbnail && cached.reason == reason) {
                cached.row
            } else {
                val projected = task.toTaskUi(thumbnail, reason)
                val reused = cached?.row?.takeIf { it == projected } ?: projected
                cache[task.id] = CachedTask(task, thumbnail, reason, reused)
                reused
            }
            rows += row
            if (previousRows.getOrNull(index) !== row) unchanged = false
        }
        if (unchanged) return previousRows
        previousRows = rows
        return rows
    }
}

private fun DownloadTask.toTaskUi(thumbnail: String?, reason: String?) = DownloadTaskUi(
    id = id,
    title = title,
    platform = platform.toUi(),
    stage = when (stage) {
        TaskStage.QUEUED -> DownloadTaskStage.Queued
        TaskStage.RESOLVING -> DownloadTaskStage.Resolving
        TaskStage.DOWNLOADING -> DownloadTaskStage.Downloading
        TaskStage.MERGING -> DownloadTaskStage.Merging
        TaskStage.COMPLETED -> DownloadTaskStage.Completed
        TaskStage.FAILED -> DownloadTaskStage.Failed
        TaskStage.CANCELED -> DownloadTaskStage.Canceled
    },
    formatLabel = when (preset) {
        QualityPreset.RECOMMENDED -> "推荐"
        QualityPreset.BEST -> "最高画质"
        QualityPreset.DATA_SAVER -> "节省空间"
        QualityPreset.AUDIO_ONLY -> "仅音频"
    },
    mediaKind = mediaKind.toUi(),
    galleryOutputMode = galleryOutputMode?.toUi(),
    outputLocations = resolvedOutputLocations,
    progress = if (stage == TaskStage.QUEUED || stage == TaskStage.RESOLVING) null else progress,
    downloadedBytes = if (stage == TaskStage.COMPLETED) outputSizeBytes else downloadedBytes,
    totalBytes = if (stage == TaskStage.COMPLETED) outputSizeBytes.takeIf { it > 0 } else totalBytes,
    bytesPerSecond = bytesPerSecond,
    thumbnailUrl = thumbnail,
    etaSeconds = etaSeconds,
    errorMessage = reason,
)

internal fun Platform.toUi() = when (this) {
    Platform.DOUYIN -> MediaPlatform.Douyin
    Platform.BILIBILI -> MediaPlatform.Bilibili
    Platform.XIAOHONGSHU -> MediaPlatform.Xiaohongshu
    Platform.WEIBO -> MediaPlatform.Weibo
    Platform.KUAISHOU -> MediaPlatform.Kuaishou
}

internal fun MediaKind.toUi() = when (this) {
    MediaKind.VIDEO -> MediaKindUi.Video
    MediaKind.GALLERY -> MediaKindUi.Gallery
}

internal fun GalleryOutputMode.toUi() = when (this) {
    GalleryOutputMode.IMAGES -> GalleryOutputModeUi.Images
    GalleryOutputMode.AUDIO -> GalleryOutputModeUi.Audio
    GalleryOutputMode.MP4 -> GalleryOutputModeUi.Mp4
}
