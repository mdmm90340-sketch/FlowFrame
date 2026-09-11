package com.flowframe.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EditorRecoverySnapshotTest {
    @Test
    fun progressAppearanceAndDiagnosticsDoNotChangeRecoveryState() {
        val original = editor()
        val unrelatedUpdates = original.copy(
            tasks = TasksUiState(items = listOf(DownloadTaskUi(
                id = "task", title = "Generated", platform = MediaPlatform.Douyin,
                stage = DownloadTaskStage.Downloading, formatLabel = "推荐", progress = 0.5f,
            ))),
            settings = SettingsUiState(themeMode = ThemeMode.Dark),
            overlay = FlowFrameOverlay.Diagnostics,
            diagnosticsText = "Generated diagnostics",
        )
        assertEquals(original.snapshot(), unrelatedUpdates.snapshot())
    }

    @Test
    fun selectedImagesQualityAudioAndOutputModeRemainRecoverable() {
        val original = editor()
        val updated = original.copy(activePreview = original.activePreview!!.copy(
            selectedImageIndices = setOf(0, 2), selectedPresetId = "best", audioOnly = true,
            selectedGalleryOutputMode = GalleryOutputModeUi.Images,
        ))
        val snapshot = updated.snapshot()
        assertNotEquals(original.snapshot(), snapshot)
        assertEquals(setOf(0, 2), snapshot.preview!!.selectedImages)
        assertEquals("best", snapshot.preview.preset)
        assertEquals(true, snapshot.preview.audioOnly)
        assertEquals("Images", snapshot.preview.galleryMode)
        assertFalse(snapshot.toString().contains("ephemeral"))
    }

    @Test
    fun pendingRestorePreservesExistingKeysButLeavingPreviewClearsThem() {
        val pending = editor().editorRecoverySnapshot(previewUrl = null, restoringPreview = true)
        assertEquals("draft", pending.input)
        assertNull(pending.preview)
        val dismissed = editor().copy(activePreview = null).editorRecoverySnapshot(null, false)
        assertNotNull(dismissed.preview)
        assertNull(dismissed.preview!!.url)
        assertNull(dismissed.preview.selectedImages)
        assertNull(dismissed.preview.preset)
        assertNull(dismissed.preview.audioOnly)
        assertNull(dismissed.preview.galleryMode)
    }

    @Test
    fun inputAndDestinationChangesRemainRecoverable() {
        val original = editor()
        val input = original.copy(home = original.home.copy(linkText = "another draft"))
        val destination = original.copy(selectedDestination = FlowFrameDestination.Tasks)
        assertNotEquals(original.snapshot(), input.snapshot())
        assertEquals("another draft", input.snapshot().input)
        assertNotEquals(original.snapshot(), destination.snapshot())
        assertEquals("Tasks", destination.snapshot().destination)
    }

    private fun FlowFrameUiState.snapshot() = editorRecoverySnapshot("https://example.invalid/post", false)

    private fun editor() = FlowFrameUiState(
        home = HomeUiState(linkText = "draft"),
        activePreview = MediaPreviewUi(
            id = "preview", title = "Generated preview", author = "Generated", platform = MediaPlatform.Douyin,
            durationLabel = "", mediaKind = MediaKindUi.Gallery, imageCount = 3,
            imageUrls = listOf("https://cdn.example/ephemeral-1", "https://cdn.example/ephemeral-2", "https://cdn.example/ephemeral-3"),
            selectedImageIndices = setOf(1), selectedPresetId = "recommended",
        ),
    )
}
