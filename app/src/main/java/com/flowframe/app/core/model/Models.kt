package com.flowframe.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Platform {
    DOUYIN,
    BILIBILI,
    XIAOHONGSHU,
    WEIBO,
    KUAISHOU,
}

@Serializable
enum class QualityPreset {
    RECOMMENDED,
    BEST,
    DATA_SAVER,
    AUDIO_ONLY,
}

@Serializable
enum class MediaKind {
    VIDEO,
    GALLERY,
}

@Serializable
enum class GalleryOutputMode {
    IMAGES,
    AUDIO,
    MP4,
}

@Serializable
enum class TaskStage {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    MERGING,
    COMPLETED,
    FAILED,
    CANCELED,
}

@Serializable
data class MediaPreview(
    val sourceUrl: String,
    val platform: Platform,
    val mediaId: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val estimatedSizeBytes: Long = 0,
    val mediaKind: MediaKind = MediaKind.VIDEO,
    val imageCount: Int = 0,
    val hasAudio: Boolean = false,
    val imageUrls: List<String> = emptyList(),
    val formats: List<MediaFormat> = emptyList(),
) {
    val stableKey: String get() = "${platform.name}:$mediaId"
}

@Serializable
data class MediaFormat(
    val id: String = "",
    val ext: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val filesizeBytes: Long = 0,
)

@Serializable
data class DownloadTask(
    val id: String,
    val sourceUrl: String,
    val platform: Platform,
    val mediaId: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val preset: QualityPreset,
    val stage: TaskStage = TaskStage.QUEUED,
    val progress: Float = 0f,
    val etaSeconds: Long? = null,
    val outputLocation: String? = null,
    val outputSizeBytes: Long = 0,
    val errorMessage: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val mediaKind: MediaKind = MediaKind.VIDEO,
    val galleryOutputMode: GalleryOutputMode? = null,
    val outputLocations: List<String> = emptyList(),
    val selectedImageIndices: List<Int>? = null,
    val outputDirectoryUri: String? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
) {
    /**
     * New gallery tasks can publish several MediaStore items. Legacy video tasks only have
     * [outputLocation], so consumers should use this compatibility view when opening output.
     */
    val resolvedOutputLocations: List<String>
        get() = outputLocations.ifEmpty { listOfNotNull(outputLocation) }
}
