package com.flowframe.app.ui.model

import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.MediaKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowFrameUiStateTest {
    @Test
    fun portraitResolutionUsesShortEdgeAndKeepsMetadataDetails() {
        val preview = videoPreview(
            width = 1080,
            height = 1920,
            metadataLabel = "1920P · 公开内容",
        )

        assertEquals("1080P · 1080×1920 · 竖屏", preview.resolutionLabel)
        assertEquals(
            "1080P · 1080×1920 · 竖屏 · 公开内容",
            preview.displayMetadataLabel,
        )
    }

    @Test
    fun landscapeAndSquareResolutionLabelsRemainOrientationAware() {
        assertEquals(
            "1080P · 1920×1080 · 横屏",
            videoPreview(width = 1920, height = 1080).resolutionLabel,
        )
        assertEquals(
            "1080P · 1080×1080 · 方形",
            videoPreview(width = 1080, height = 1080).resolutionLabel,
        )
    }

    @Test
    fun galleryDefaultsToMp4AndDisablesAudioWhenTrackIsMissing() {
        val preview = galleryPreview(hasAudio = false)
        val audioOption = preview.galleryOutputOptions.single {
            it.mode == GalleryOutputModeUi.Audio
        }

        assertEquals(GalleryOutputModeUi.Mp4, preview.selectedGalleryOutputMode)
        assertEquals("6 张", preview.previewBadgeLabel)
        assertFalse(audioOption.available)
        assertEquals("该图集没有可用配乐", audioOption.unavailableReason)
        assertTrue(preview.selectedOutputAvailable)
    }

    @Test
    fun galleryAudioSelectionOnlyBecomesAvailableWithAnAudioTrack() {
        assertFalse(
            galleryPreview(
                hasAudio = false,
                selectedMode = GalleryOutputModeUi.Audio,
            ).selectedOutputAvailable,
        )
        assertTrue(
            galleryPreview(
                hasAudio = true,
                selectedMode = GalleryOutputModeUi.Audio,
            ).selectedOutputAvailable,
        )
    }

    @Test
    fun completedImageTaskReportsEveryPublishedImage() {
        val task = DownloadTaskUi(
            id = "gallery-task",
            title = "图文作品",
            platform = MediaPlatform.Douyin,
            stage = DownloadTaskStage.Completed,
            formatLabel = "推荐",
            mediaKind = MediaKindUi.Gallery,
            galleryOutputMode = GalleryOutputModeUi.Images,
            outputLocations = List(8) { "content://media/image/$it" },
        )

        assertTrue(task.isImageGalleryOutput)
        assertEquals("图片", task.displayFormatLabel)
        assertEquals("已保存 8 张", task.completedGallerySummary)
    }

    @Test
    fun overflowMenuDeletesTerminalRecordsAndCancelsUnfinishedTasks() {
        assertEquals(TaskOverflowAction.DeleteRecord, DownloadTaskStage.Completed.overflowAction)
        assertEquals(TaskOverflowAction.DeleteRecord, DownloadTaskStage.Failed.overflowAction)
        assertEquals(TaskOverflowAction.CancelTask, DownloadTaskStage.Queued.overflowAction)
        assertEquals(TaskOverflowAction.CancelTask, DownloadTaskStage.Resolving.overflowAction)
        assertEquals(TaskOverflowAction.CancelTask, DownloadTaskStage.Downloading.overflowAction)
        assertEquals(TaskOverflowAction.CancelTask, DownloadTaskStage.Merging.overflowAction)
        assertEquals(TaskOverflowAction.CancelTask, DownloadTaskStage.Paused.overflowAction)
    }

    @Test
    fun legacyTaskJsonKeepsVideoAndSingleOutputDefaults() {
        val task = Json.decodeFromString<DownloadTask>(
            """
                {
                  "id":"legacy",
                  "sourceUrl":"https://example.invalid/video",
                  "platform":"DOUYIN",
                  "mediaId":"old-video",
                  "title":"旧任务",
                  "preset":"RECOMMENDED",
                  "outputLocation":"content://media/video/1"
                }
            """.trimIndent(),
        )

        assertEquals(MediaKind.VIDEO, task.mediaKind)
        assertEquals(null, task.galleryOutputMode)
        assertTrue(task.outputLocations.isEmpty())
        assertEquals(listOf("content://media/video/1"), task.resolvedOutputLocations)
    }

    private fun videoPreview(
        width: Int,
        height: Int,
        metadataLabel: String = "",
    ) = MediaPreviewUi(
        id = "video",
        title = "视频",
        author = "作者",
        platform = MediaPlatform.Bilibili,
        durationLabel = "1:00",
        metadataLabel = metadataLabel,
        width = width,
        height = height,
    )

    private fun galleryPreview(
        hasAudio: Boolean,
        selectedMode: GalleryOutputModeUi = GalleryOutputModeUi.Mp4,
    ) = MediaPreviewUi(
        id = "gallery",
        title = "图文作品",
        author = "作者",
        platform = MediaPlatform.Douyin,
        durationLabel = "",
        mediaKind = MediaKindUi.Gallery,
        imageCount = 6,
        hasAudio = hasAudio,
        selectedGalleryOutputMode = selectedMode,
        canDownload = true,
    )
}
