package com.flowframe.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.flowframe.app.ui.components.FlowFrameBrandHeader
import com.flowframe.app.ui.components.StatePanel
import com.flowframe.app.ui.model.SettingsUiState
import com.flowframe.app.ui.model.ThemeMode

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onOutputDirectoryRequested: () -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onMaxConcurrentChanged: (Int) -> Unit,
    onResetOutputDirectoryRequested: () -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onDiagnosticsRequested: () -> Unit,
    onAboutRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            FlowFrameBrandHeader(
                title = "下载与外观",
            )
        }

        item {
            Column {
                Text("保存设置", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "选择保存目录、网络条件与界面外观。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.outputDirectoryAvailable) {
            item {
                StatePanel(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "保存目录不可用",
                    message = "目录可能已移动或权限已失效，请重新选择。",
                    actionLabel = "更换目录",
                    isError = true,
                    onAction = onOutputDirectoryRequested,
                )
            }
        }

        item {
            SettingsSection(title = "下载") {
                SettingRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = "保存位置",
                    subtitle = state.outputDirectoryLabel,
                    onClick = onOutputDirectoryRequested,
                )
                if (state.customOutputDirectory) {
                    TextButton(
                        onClick = onResetOutputDirectoryRequested,
                        modifier = Modifier.padding(start = 58.dp, bottom = 4.dp),
                    ) { Text("恢复系统默认目录") }
                }
                SectionDivider()
                SettingRow(
                    icon = Icons.Rounded.Wifi,
                    title = "仅使用 Wi‑Fi",
                    subtitle = "任务仅在 Wi-Fi 连接下下载",
                    trailing = {
                        Switch(
                            checked = state.wifiOnly,
                            onCheckedChange = onWifiOnlyChanged,
                            modifier = Modifier.semantics { contentDescription = "仅使用 Wi-Fi" },
                        )
                    },
                )
                SectionDivider()
                SettingRow(
                    icon = Icons.Rounded.Speed,
                    title = "并发任务",
                    subtitle = "建议保持 2 个以兼顾速度和稳定性",
                    trailing = {
                        CounterControl(
                            value = state.maxConcurrentDownloads,
                            onValueChanged = onMaxConcurrentChanged,
                        )
                    },
                )
            }
        }

        item {
            SettingsSection(title = "外观") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingIcon(Icons.Rounded.Palette)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("主题模式", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "跟随设备设置，或固定使用浅色、深色",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.values().forEach { mode ->
                            FilterChip(
                                selected = state.themeMode == mode,
                                onClick = { onThemeModeChanged(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                SectionDivider()
                SettingRow(
                    icon = Icons.Rounded.Palette,
                    title = "系统动态配色",
                    subtitle = if (state.dynamicColorAvailable) "使用设备壁纸生成的颜色" else "需要 Android 12 或更高版本",
                    trailing = {
                        Switch(
                            checked = state.dynamicColor && state.dynamicColorAvailable,
                            onCheckedChange = onDynamicColorChanged,
                            enabled = state.dynamicColorAvailable,
                            modifier = Modifier.semantics { contentDescription = "系统动态配色" },
                        )
                    },
                )
            }
        }

        item {
            SettingsSection(title = "支持") {
                SettingRow(
                    icon = Icons.Rounded.BugReport,
                    title = "诊断信息",
                    subtitle = "查看与复制设备、解析器和任务状态",
                    onClick = onDiagnosticsRequested,
                )
                SectionDivider()
                SettingRow(
                    icon = Icons.Rounded.Info,
                    title = "关于流影",
                    subtitle = "${state.versionLabel} · 开源许可与隐私说明",
                    onClick = onAboutRequested,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Row(
        modifier = clickableModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CounterControl(
    value: Int,
    onValueChanged: (Int) -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChanged((value - 1).coerceAtLeast(1)) },
                enabled = value > 1,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "减少并发任务")
            }
            Text(
                text = value.coerceIn(1, 3).toString(),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = { onValueChanged((value + 1).coerceAtMost(3)) },
                enabled = value < 3,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "增加并发任务")
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.System -> "跟随系统"
        ThemeMode.Light -> "浅色"
        ThemeMode.Dark -> "深色"
    }
