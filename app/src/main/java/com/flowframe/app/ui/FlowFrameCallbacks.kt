package com.flowframe.app.ui

import com.flowframe.app.ui.model.FlowFrameDestination
import com.flowframe.app.ui.model.GalleryOutputModeUi
import com.flowframe.app.ui.model.TaskAction
import com.flowframe.app.ui.model.TaskFilter
import com.flowframe.app.ui.model.TaskOutputAction
import com.flowframe.app.ui.model.ThemeMode

interface FlowFrameCallbacks {
    fun onDestinationSelected(destination: FlowFrameDestination) = Unit
    fun onLinkChanged(value: String) = Unit
    fun onPasteRequested() = Unit
    fun onClearLinkRequested() = Unit
    fun onParseRequested() = Unit
    fun onParseErrorAction() = Unit
    fun onRecentMediaSelected(mediaId: String) = Unit
    fun onPreviewBack() = Unit
    fun onPresetSelected(presetId: String) = Unit
    fun onGalleryOutputModeSelected(mode: GalleryOutputModeUi) = Unit
    fun onAudioOnlyChanged(enabled: Boolean) = Unit
    fun onContentSelectionRequested() = Unit
    fun onFormatDetailsRequested() = Unit
    fun onDownloadRequested() = Unit
    fun onTaskFilterSelected(filter: TaskFilter) = Unit
    fun onTaskAction(taskId: String, action: TaskAction) = Unit
    fun onTaskOutputAction(taskId: String, action: TaskOutputAction) {
        onTaskAction(
            taskId = taskId,
            action = when (action) {
                is TaskOutputAction.Open -> TaskAction.Open
                is TaskOutputAction.Share -> TaskAction.Share
            },
        )
    }
    fun onOutputDirectoryRequested() = Unit
    fun onWifiOnlyChanged(enabled: Boolean) = Unit
    fun onMaxConcurrentDownloadsChanged(count: Int) = Unit
    fun onCredentialsRequested() = Unit
    fun onThemeModeChanged(mode: ThemeMode) = Unit
    fun onDynamicColorChanged(enabled: Boolean) = Unit
    fun onDiagnosticsRequested() = Unit
    fun onAboutRequested() = Unit
    fun onImageSelectionChanged(index: Int, selected: Boolean) = Unit
    fun onSelectAllImages(selected: Boolean) = Unit
    fun onOverlayDismissed() = Unit
    fun onCopyDiagnosticsRequested() = Unit
    fun onOpenRepositoryRequested() = Unit
    fun onResetOutputDirectoryRequested() = Unit
}

object NoOpFlowFrameCallbacks : FlowFrameCallbacks
