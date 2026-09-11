package com.flowframe.app.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class FlowFrameUiState(
    val selectedDestination: FlowFrameDestination = FlowFrameDestination.Home,
    val home: HomeUiState = HomeUiState(),
    val activePreview: MediaPreviewUi? = null,
    val tasks: TasksUiState = TasksUiState(),
    val settings: SettingsUiState = SettingsUiState(),
    val overlay: FlowFrameOverlay? = null,
    val diagnosticsText: String = "",
)

enum class FlowFrameOverlay { Gallery, FormatDetails, Diagnostics, About }

enum class FlowFrameDestination {
    Home,
    Tasks,
    Settings,
}

enum class MediaPlatform {
    Douyin,
    Bilibili,
    Xiaohongshu,
    Weibo,
    Kuaishou,
    Unknown,
}

enum class MediaKindUi {
    Video,
    Gallery,
}

enum class GalleryOutputModeUi {
    Images,
    Audio,
    Mp4,
}

@Immutable
data class HomeUiState(
    val linkText: String = "",
    val detectedPlatform: MediaPlatform? = null,
    val inputMessage: String? = null,
    val inputMessageIsError: Boolean = false,
    val canParse: Boolean = false,
    val canPaste: Boolean = false,
    val parseState: ParseUiState = ParseUiState.Idle,
    val recentItems: List<RecentMediaUi> = emptyList(),
)

sealed interface ParseUiState {
    object Idle : ParseUiState

    @Immutable
    data class Loading(
        val stage: ParseStage = ParseStage.Recognizing,
    ) : ParseUiState

    @Immutable
    data class Error(
        val title: String = "暂时无法解析",
        val message: String,
        val actionLabel: String? = "重试",
    ) : ParseUiState
}

enum class ParseStage {
    Recognizing,
    FetchingMetadata,
    ResolvingFormats,
}

@Immutable
data class RecentMediaUi(
    val id: String,
    val title: String,
    val author: String,
    val platform: MediaPlatform,
    val metadata: String,
    val thumbnailUrl: String? = null,
)

@Immutable
data class MediaPreviewUi(
    val id: String,
    val title: String,
    val author: String,
    val platform: MediaPlatform,
    val durationLabel: String,
    val metadataLabel: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val mediaKind: MediaKindUi = MediaKindUi.Video,
    val imageCount: Int = 0,
    val hasAudio: Boolean = false,
    val selectedGalleryOutputMode: GalleryOutputModeUi = GalleryOutputModeUi.Mp4,
    val description: String = "",
    val contentCount: Int = 1,
    val selectedContentCount: Int = 1,
    val presets: List<FormatPresetUi> = emptyList(),
    val selectedPresetId: String? = null,
    val audioOnly: Boolean = false,
    val estimatedSizeLabel: String? = null,
    val destinationLabel: String = "默认目录",
    val canDownload: Boolean = false,
    val thumbnailUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val selectedImageIndices: Set<Int> = (0 until imageCount).toSet(),
    val formatDetails: String = "",
) {
    val resolvedImageCount: Int
        get() = imageCount.coerceAtLeast(0)

    val selectedImageCount: Int
        get() = selectedImageIndices.count { it in 0 until resolvedImageCount }

    val resolutionLabel: String
        get() {
            if (width <= 0 || height <= 0) return ""
            val orientation = when {
                height > width -> "竖屏"
                width > height -> "横屏"
                else -> "方形"
            }
            return "${minOf(width, height)}P · ${width}×${height} · $orientation"
        }

    val displayMetadataLabel: String
        get() {
            if (resolutionLabel.isBlank()) return metadataLabel
            val legacyDetails = metadataLabel
                .split(" · ")
                .filterNot { it.matches(legacyResolutionLabel) }
                .joinToString(" · ")
            return listOf(resolutionLabel, legacyDetails)
                .filter(String::isNotBlank)
                .joinToString(" · ")
        }

    val previewBadgeLabel: String
        get() = when (mediaKind) {
            MediaKindUi.Video -> durationLabel
            MediaKindUi.Gallery -> resolvedImageCount.takeIf { it > 0 }?.let { "$it 张" }.orEmpty()
        }

    val galleryOutputOptions: List<GalleryOutputOptionUi>
        get() = listOf(
            GalleryOutputOptionUi(
                mode = GalleryOutputModeUi.Images,
                title = "保存图片",
                subtitle = "逐张保存原图",
                detail = resolvedImageCount.takeIf { it > 0 }?.let { "已选 $selectedImageCount / $it 张，保留原始顺序" }
                    ?: "保留作品中的全部图片",
                available = resolvedImageCount > 0,
                unavailableReason = "没有找到可保存的图片",
            ),
            GalleryOutputOptionUi(
                mode = GalleryOutputModeUi.Audio,
                title = "仅保存配乐",
                subtitle = "提取作品原声",
                detail = "适合离线聆听或二次整理",
                available = hasAudio,
                unavailableReason = "该图集没有可用配乐",
            ),
            GalleryOutputOptionUi(
                mode = GalleryOutputModeUi.Mp4,
                title = "合成 MP4",
                subtitle = "图片与配乐自动成片",
                detail = if (hasAudio) "跟随作品节奏，生成兼容视频" else "无配乐时生成静音视频",
                badge = "推荐",
                available = resolvedImageCount > 0,
                unavailableReason = "没有足够内容生成视频",
            ),
        )

    val selectedOutputAvailable: Boolean
        get() = when (mediaKind) {
            MediaKindUi.Video -> (audioOnly && hasAudio) || (!audioOnly && presets.any {
                it.id == selectedPresetId && it.available
            })

            MediaKindUi.Gallery -> galleryOutputOptions.any {
                it.mode == selectedGalleryOutputMode && it.available
            } && (selectedGalleryOutputMode == GalleryOutputModeUi.Audio || selectedImageCount > 0)
        }
}

@Immutable
data class GalleryOutputOptionUi(
    val mode: GalleryOutputModeUi,
    val title: String,
    val subtitle: String,
    val detail: String,
    val badge: String? = null,
    val available: Boolean = true,
    val unavailableReason: String? = null,
)

@Immutable
data class FormatPresetUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val detail: String,
    val badge: String? = null,
    val available: Boolean = true,
    val unavailableReason: String? = null,
)

@Immutable
data class TasksUiState(
    val selectedFilter: TaskFilter = TaskFilter.All,
    val isOffline: Boolean = false,
    val items: List<DownloadTaskUi> = emptyList(),
) {
    val activeTaskCount: Int
        get() = items.count { it.stage.isActive }
}

enum class TaskFilter {
    All,
    Active,
    Completed,
    Failed,
    Canceled,
}

enum class DownloadTaskStage {
    Queued,
    Resolving,
    Downloading,
    Merging,
    Paused,
    Completed,
    Failed,
    Canceled;

    val isActive: Boolean
        get() = this == Queued || this == Resolving || this == Downloading || this == Merging

    val overflowAction: TaskOverflowAction
        get() = when (this) {
            Completed,
            Failed,
            Canceled -> TaskOverflowAction.DeleteRecord
            else -> TaskOverflowAction.CancelTask
        }
}

enum class TaskOverflowAction {
    CancelTask,
    DeleteRecord,
}

enum class TaskAction {
    Pause,
    Resume,
    Cancel,
    Retry,
    Open,
    Share,
    Delete,
}

sealed interface TaskOutputAction {
    @Immutable
    data class Open(
        val outputIndex: Int = 0,
    ) : TaskOutputAction

    /** A null list means share every output; a non-null list targets specific output indexes. */
    @Immutable
    data class Share(
        val outputIndexes: List<Int>? = null,
    ) : TaskOutputAction
}

@Immutable
data class DownloadTaskUi(
    val id: String,
    val title: String,
    val platform: MediaPlatform,
    val stage: DownloadTaskStage,
    val formatLabel: String,
    val mediaKind: MediaKindUi = MediaKindUi.Video,
    val galleryOutputMode: GalleryOutputModeUi? = null,
    val outputLocations: List<String> = emptyList(),
    val progress: Float? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
    val etaSeconds: Long? = null,
    val errorMessage: String? = null,
    val thumbnailUrl: String? = null,
) {
    val isImageGalleryOutput: Boolean
        get() = mediaKind == MediaKindUi.Gallery && galleryOutputMode == GalleryOutputModeUi.Images

    val displayFormatLabel: String
        get() = galleryOutputMode?.label ?: formatLabel

    val completedGallerySummary: String?
        get() = if (isImageGalleryOutput && outputLocations.isNotEmpty()) {
            "已保存 ${outputLocations.size} 张"
        } else {
            null
        }
}

@Immutable
data class SettingsUiState(
    val outputDirectoryLabel: String = "尚未选择保存目录",
    val outputDirectoryAvailable: Boolean = true,
    val wifiOnly: Boolean = false,
    val maxConcurrentDownloads: Int = 2,
    val credentialsConfigured: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val versionLabel: String = "0.1.0",
    val dynamicColorAvailable: Boolean = true,
    val customOutputDirectory: Boolean = false,
)

enum class ThemeMode {
    System,
    Light,
    Dark,
}

val GalleryOutputModeUi.label: String
    get() = when (this) {
        GalleryOutputModeUi.Images -> "图片"
        GalleryOutputModeUi.Audio -> "音频"
        GalleryOutputModeUi.Mp4 -> "MP4"
    }

private val legacyResolutionLabel = Regex("""\d+[pP]""")
