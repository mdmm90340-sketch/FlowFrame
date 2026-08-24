package com.flowframe.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flowframe.app.ui.model.FlowFrameDestination
import com.flowframe.app.ui.model.FlowFrameUiState
import com.flowframe.app.ui.model.ThemeMode
import com.flowframe.app.ui.screens.HomeScreen
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

    FlowFrameTheme(
        darkTheme = darkTheme,
        dynamicColor = uiState.settings.dynamicColor,
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val preview = uiState.activePreview
            if (preview != null) {
                PreviewScreen(
                    state = preview,
                    onBack = callbacks::onPreviewBack,
                    onPresetSelected = callbacks::onPresetSelected,
                    onGalleryOutputModeSelected = callbacks::onGalleryOutputModeSelected,
                    onAudioOnlyChanged = callbacks::onAudioOnlyChanged,
                    onContentSelectionRequested = callbacks::onContentSelectionRequested,
                    onFormatDetailsRequested = callbacks::onFormatDetailsRequested,
                    onDownloadRequested = callbacks::onDownloadRequested,
                )
            } else {
                MainScaffold(
                    uiState = uiState,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    uiState: FlowFrameUiState,
    callbacks: FlowFrameCallbacks,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FlowFrameNavigationBar(
                selectedDestination = uiState.selectedDestination,
                activeTaskCount = uiState.tasks.activeTaskCount,
                onDestinationSelected = callbacks::onDestinationSelected,
            )
        },
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.selectedDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                    onWifiOnlyChanged = callbacks::onWifiOnlyChanged,
                    onMaxConcurrentChanged = callbacks::onMaxConcurrentDownloadsChanged,
                    onCredentialsRequested = callbacks::onCredentialsRequested,
                    onThemeModeChanged = callbacks::onThemeModeChanged,
                    onDynamicColorChanged = callbacks::onDynamicColorChanged,
                    onDiagnosticsRequested = callbacks::onDiagnosticsRequested,
                    onAboutRequested = callbacks::onAboutRequested,
                )
            }
        }
    }
}

@Composable
private fun FlowFrameNavigationBar(
    selectedDestination: FlowFrameDestination,
    activeTaskCount: Int,
    onDestinationSelected: (FlowFrameDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
    ) {
        FlowFrameDestination.values().forEach { destination ->
            val item = destination.navigationItem
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    NavigationIcon(
                        icon = item.icon,
                        badgeCount = if (destination == FlowFrameDestination.Tasks) activeTaskCount else 0,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun NavigationIcon(
    icon: ImageVector,
    badgeCount: Int,
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null)
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = (-7).dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }
    }
}

private data class NavigationItem(
    val label: String,
    val icon: ImageVector,
)

private val FlowFrameDestination.navigationItem: NavigationItem
    get() = when (this) {
        FlowFrameDestination.Home -> NavigationItem("首页", Icons.Rounded.Home)
        FlowFrameDestination.Tasks -> NavigationItem("任务", Icons.Rounded.Download)
        FlowFrameDestination.Settings -> NavigationItem("设置", Icons.Rounded.Settings)
    }
