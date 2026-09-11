package com.flowframe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flowframe.app.ui.model.FlowFrameDestination
import com.flowframe.app.ui.model.FlowFrameOverlay
import com.flowframe.app.ui.model.FlowFrameUiState
import com.flowframe.app.ui.model.ThemeMode
import com.flowframe.app.ui.screens.GalleryBrowserScreen
import com.flowframe.app.ui.screens.HomeScreen
import com.flowframe.app.ui.screens.InformationScreen
import com.flowframe.app.ui.screens.PreviewScreen
import com.flowframe.app.ui.screens.SettingsScreen
import com.flowframe.app.ui.screens.TasksScreen
import com.flowframe.app.ui.theme.FlowFrameTheme

@Composable
fun FlowFrameApp(
    uiState: FlowFrameUiState,
    modifier: Modifier = Modifier,
    callbacks: FlowFrameCallbacks = NoOpFlowFrameCallbacks,
) {
    val darkTheme = when (uiState.settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    FlowFrameTheme(darkTheme = darkTheme, dynamicColor = uiState.settings.dynamicColor) {
        BackHandler(
            enabled = uiState.overlay != null || uiState.activePreview != null ||
                uiState.selectedDestination != FlowFrameDestination.Home,
        ) {
            when {
                uiState.overlay != null -> callbacks.onOverlayDismissed()
                uiState.activePreview != null -> callbacks.onPreviewBack()
                else -> callbacks.onDestinationSelected(FlowFrameDestination.Home)
            }
        }
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val preview = uiState.activePreview
            when {
                uiState.overlay == FlowFrameOverlay.Gallery && preview != null -> GalleryBrowserScreen(
                    state = preview,
                    onBack = callbacks::onOverlayDismissed,
                    onImageSelectionChanged = callbacks::onImageSelectionChanged,
                    onSelectAllImages = callbacks::onSelectAllImages,
                )
                uiState.overlay != null -> InformationScreen(
                    overlay = uiState.overlay,
                    uiState = uiState,
                    onBack = callbacks::onOverlayDismissed,
                    onCopyDiagnostics = callbacks::onCopyDiagnosticsRequested,
                    onOpenRepository = callbacks::onOpenRepositoryRequested,
                )
                preview != null -> PreviewScreen(
                    state = preview,
                    onBack = callbacks::onPreviewBack,
                    onPresetSelected = callbacks::onPresetSelected,
                    onGalleryOutputModeSelected = callbacks::onGalleryOutputModeSelected,
                    onAudioOnlyChanged = callbacks::onAudioOnlyChanged,
                    onContentSelectionRequested = callbacks::onContentSelectionRequested,
                    onFormatDetailsRequested = callbacks::onFormatDetailsRequested,
                    onDownloadRequested = callbacks::onDownloadRequested,
                )
                else -> MainScaffold(uiState, callbacks)
            }
        }
    }
}

@Composable
private fun MainScaffold(uiState: FlowFrameUiState, callbacks: FlowFrameCallbacks) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!expanded) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    FlowFrameDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == uiState.selectedDestination,
                            onClick = { callbacks.onDestinationSelected(destination) },
                            icon = { DestinationIcon(destination, uiState.tasks.activeTaskCount) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                if (expanded) NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    FlowFrameDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == uiState.selectedDestination,
                            onClick = { callbacks.onDestinationSelected(destination) },
                            icon = { DestinationIcon(destination, uiState.tasks.activeTaskCount) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Crossfade(
                        targetState = uiState.selectedDestination,
                        modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                        label = "main_destination",
                    ) { destination ->
                        when (destination) {
                            FlowFrameDestination.Home -> HomeScreen(
                                state = uiState.home,
                                onLinkChanged = callbacks::onLinkChanged,
                                onPasteRequested = callbacks::onPasteRequested,
                                onClearRequested = callbacks::onClearLinkRequested,
                                onParseRequested = callbacks::onParseRequested,
                                onErrorAction = callbacks::onParseErrorAction,
                                onRecentSelected = callbacks::onRecentMediaSelected,
                            )
                            FlowFrameDestination.Tasks -> TasksScreen(
                                state = uiState.tasks,
                                onFilterSelected = callbacks::onTaskFilterSelected,
                                onTaskAction = callbacks::onTaskAction,
                                onTaskOutputAction = callbacks::onTaskOutputAction,
                            )
                            FlowFrameDestination.Settings -> SettingsScreen(
                                state = uiState.settings,
                                onOutputDirectoryRequested = callbacks::onOutputDirectoryRequested,
                                onResetOutputDirectoryRequested = callbacks::onResetOutputDirectoryRequested,
                                onWifiOnlyChanged = callbacks::onWifiOnlyChanged,
                                onMaxConcurrentChanged = callbacks::onMaxConcurrentDownloadsChanged,
                                onThemeModeChanged = callbacks::onThemeModeChanged,
                                onDynamicColorChanged = callbacks::onDynamicColorChanged,
                                onDiagnosticsRequested = callbacks::onDiagnosticsRequested,
                                onAboutRequested = callbacks::onAboutRequested,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: FlowFrameDestination, activeCount: Int) {
    BadgedBox(badge = {
        if (destination == FlowFrameDestination.Tasks && activeCount > 0) Badge {
            Text(if (activeCount > 99) "99+" else activeCount.toString())
        }
    }) {
        Icon(destination.icon, contentDescription = null)
    }
}

private val FlowFrameDestination.label: String
    get() = when (this) {
        FlowFrameDestination.Home -> "首页"
        FlowFrameDestination.Tasks -> "任务"
        FlowFrameDestination.Settings -> "设置"
    }

private val FlowFrameDestination.icon: ImageVector
    get() = when (this) {
        FlowFrameDestination.Home -> Icons.Rounded.Home
        FlowFrameDestination.Tasks -> Icons.Rounded.Download
        FlowFrameDestination.Settings -> Icons.Rounded.Settings
    }
