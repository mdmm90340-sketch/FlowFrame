package com.flowframe.app.ui.model

/** Persistable editor state; task progress, diagnostics, and appearance do not belong here. */
internal data class EditorRecoverySnapshot(
    val input: String,
    val destination: String,
    val preview: PreviewRecoverySnapshot?,
)

internal data class PreviewRecoverySnapshot(
    val url: String?,
    val selectedImages: Set<Int>?,
    val preset: String?,
    val audioOnly: Boolean?,
    val galleryMode: String?,
)

internal fun FlowFrameUiState.editorRecoverySnapshot(
    previewUrl: String?,
    restoringPreview: Boolean,
) = EditorRecoverySnapshot(
    input = home.linkText,
    destination = selectedDestination.name,
    // A pending restore preserves the existing preview keys until parsing finishes.
    // A concrete snapshot with a null URL clears them when the user leaves the preview.
    preview = if (restoringPreview) null else PreviewRecoverySnapshot(
        url = previewUrl,
        selectedImages = activePreview?.selectedImageIndices,
        preset = activePreview?.selectedPresetId,
        audioOnly = activePreview?.audioOnly,
        galleryMode = activePreview?.selectedGalleryOutputMode?.name,
    ),
)
