package com.flowframe.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowframe.app.ui.components.FlowFrameBrandHeader
import com.flowframe.app.ui.components.StatePanel
import com.flowframe.app.ui.components.label
import com.flowframe.app.ui.components.platformAccent
import com.flowframe.app.ui.model.DownloadTaskStage
import com.flowframe.app.ui.model.DownloadTaskUi
import com.flowframe.app.ui.model.GalleryOutputModeUi
import com.flowframe.app.ui.model.MediaKindUi
import com.flowframe.app.ui.model.TaskAction
import com.flowframe.app.ui.model.TaskFilter
import com.flowframe.app.ui.model.TaskOutputAction
import com.flowframe.app.ui.model.TaskOverflowAction
import com.flowframe.app.ui.model.TasksUiState

@Composable
fun TasksScreen(
    state: TasksUiState,
    onFilterSelected: (TaskFilter) -> Unit,
    onTaskAction: (String, TaskAction) -> Unit,
    onTaskOutputAction: (String, TaskOutputAction) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val visibleTasks = state.items.filter { task ->
        when (state.selectedFilter) {
            TaskFilter.All -> true
            TaskFilter.Active -> task.stage.isActive || task.stage == DownloadTaskStage.Paused
            TaskFilter.Completed -> task.stage == DownloadTaskStage.Completed
            TaskFilter.Failed -> task.stage == DownloadTaskStage.Failed
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FlowFrameBrandHeader(
                title = "${state.activeTaskCount} 个进行中",
                subtitle = "任务会在后台继续",
            )
        }

        if (state.isOffline) {
            item {
                OfflineBanner()
            }
        }

        item {
            Column {
                Text("下载任务", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "查看进度、取消任务，或在失败后快速重试。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TaskFilter.values().toList(), key = { it.name }) { filter ->
                    FilterChip(
                        selected = filter == state.selectedFilter,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
        }

        if (visibleTasks.isEmpty()) {
            item {
                StatePanel(
                    icon = Icons.Rounded.CloudDownload,
                    title = if (state.items.isEmpty()) "还没有下载任务" else "这里暂时是空的",
                    message = if (state.items.isEmpty()) {
                        "从首页解析一个作品，选择保存方式后即可开始。"
                    } else {
                        "当前筛选条件下没有任务，试试其他分类。"
                    },
                )
            }
        } else {
            items(visibleTasks, key = { it.id }) { task ->
                DownloadTaskCard(
                    task = task,
                    onAction = { action -> onTaskAction(task.id, action) },
                    onOutputAction = { action -> onTaskOutputAction(task.id, action) },
                )
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column {
                Text(
                    text = "网络连接已断开",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "已完成的内容仍可打开，其他任务将在联网后继续。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskUi,
    onAction: (TaskAction) -> Unit,
    onOutputAction: (TaskOutputAction) -> Unit,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    var deleteConfirmationVisible by remember(task.id) { mutableStateOf(false) }
    val stageColor = task.stage.stageColor()
    val targetProgress = if (task.stage == DownloadTaskStage.Completed) {
        1f
    } else {
        task.progress?.coerceIn(0f, 1f) ?: 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        label = "task_progress_${task.id}",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                TaskArtwork(task)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "更多任务操作")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                when (task.stage.overflowAction) {
                                    TaskOverflowAction.DeleteRecord -> DropdownMenuItem(
                                        text = { Text("删除记录") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            deleteConfirmationVisible = true
                                        },
                                    )

                                    TaskOverflowAction.CancelTask -> DropdownMenuItem(
                                        text = { Text("取消任务") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Cancel, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onAction(TaskAction.Cancel)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = task.stage.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = stageColor,
                        )
                        Text(
                            text = "${task.platform.label} · ${task.stage.label} · ${task.displayFormatLabel}",
                            modifier = Modifier.padding(start = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            if (task.stage.isActive && task.progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = stageColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = task.progressDetail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(visible = task.errorMessage != null) {
                task.errorMessage?.let { message ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            TaskActions(
                task = task,
                onAction = onAction,
                onOutputAction = onOutputAction,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    if (deleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { deleteConfirmationVisible = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("删除任务记录？") },
            text = {
                Text("只会从 FlowFrame 的任务列表移除此记录，不会删除已经保存到相册或媒体库的文件。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmationVisible = false
                        onAction(TaskAction.Delete)
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmationVisible = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun TaskArtwork(task: DownloadTaskUi) {
    val accent = platformAccent(task.platform)
    val artworkIcon = when {
        task.mediaKind == MediaKindUi.Video -> Icons.Rounded.VideoLibrary
        task.galleryOutputMode == GalleryOutputModeUi.Images -> Icons.Rounded.PhotoLibrary
        task.galleryOutputMode == GalleryOutputModeUi.Audio -> Icons.Rounded.AudioFile
        else -> Icons.Rounded.MovieCreation
    }
    Box(
        modifier = Modifier
            .size(width = 82.dp, height = 88.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        accent.copy(alpha = 0.78f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = artworkIcon,
            contentDescription = null,
            modifier = Modifier.size(35.dp),
            tint = Color.White.copy(alpha = 0.9f),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(7.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.58f),
        ) {
            Text(
                text = task.platform.label.take(1),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TaskActions(
    task: DownloadTaskUi,
    onAction: (TaskAction) -> Unit,
    onOutputAction: (TaskOutputAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (task.stage) {
            DownloadTaskStage.Downloading -> {
                TextButton(onClick = { onAction(TaskAction.Cancel) }) {
                    Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("取消", modifier = Modifier.padding(start = 5.dp))
                }
            }

            DownloadTaskStage.Paused -> {
                TextButton(onClick = { onAction(TaskAction.Cancel) }) { Text("取消") }
                Button(onClick = { onAction(TaskAction.Resume) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("继续", modifier = Modifier.padding(start = 5.dp))
                }
            }

            DownloadTaskStage.Failed -> {
                TextButton(onClick = { onAction(TaskAction.Delete) }) { Text("删除") }
                Button(onClick = { onAction(TaskAction.Retry) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("重试", modifier = Modifier.padding(start = 5.dp))
                }
            }

            DownloadTaskStage.Completed -> {
                if (task.mediaKind == MediaKindUi.Gallery) {
                    IconButton(onClick = { onOutputAction(TaskOutputAction.Share()) }) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = if (task.isImageGalleryOutput) {
                                "分享全部图片"
                            } else {
                                "分享"
                            },
                        )
                    }
                    Button(onClick = { onOutputAction(TaskOutputAction.Open(outputIndex = 0)) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (task.isImageGalleryOutput) "打开首张" else "打开",
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                } else {
                    IconButton(onClick = { onAction(TaskAction.Share) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "分享")
                    }
                    Button(onClick = { onAction(TaskAction.Open) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("打开", modifier = Modifier.padding(start = 5.dp))
                    }
                }
            }

            DownloadTaskStage.Queued,
            DownloadTaskStage.Resolving,
            DownloadTaskStage.Merging -> TextButton(onClick = { onAction(TaskAction.Cancel) }) {
                Text("取消任务")
            }
        }
    }
}

private val TaskFilter.label: String
    get() = when (this) {
        TaskFilter.All -> "全部"
        TaskFilter.Active -> "进行中"
        TaskFilter.Completed -> "已完成"
        TaskFilter.Failed -> "失败"
    }

private val DownloadTaskStage.label: String
    get() = when (this) {
        DownloadTaskStage.Queued -> "排队中"
        DownloadTaskStage.Resolving -> "正在解析"
        DownloadTaskStage.Downloading -> "正在下载"
        DownloadTaskStage.Merging -> "正在合并"
        DownloadTaskStage.Paused -> "已暂停"
        DownloadTaskStage.Completed -> "已完成"
        DownloadTaskStage.Failed -> "失败"
    }

private val DownloadTaskStage.icon: ImageVector
    get() = when (this) {
        DownloadTaskStage.Queued -> Icons.Rounded.HourglassTop
        DownloadTaskStage.Resolving -> Icons.Rounded.Refresh
        DownloadTaskStage.Downloading -> Icons.Rounded.CloudDownload
        DownloadTaskStage.Merging -> Icons.AutoMirrored.Rounded.MergeType
        DownloadTaskStage.Paused -> Icons.Rounded.Pause
        DownloadTaskStage.Completed -> Icons.Rounded.CheckCircle
        DownloadTaskStage.Failed -> Icons.Rounded.ErrorOutline
    }

@Composable
private fun DownloadTaskStage.stageColor(): Color = when (this) {
        DownloadTaskStage.Completed -> MaterialTheme.colorScheme.secondary
        DownloadTaskStage.Failed -> MaterialTheme.colorScheme.error
        DownloadTaskStage.Paused -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
}

private val DownloadTaskUi.progressDetail: String
    get() {
        if (stage == DownloadTaskStage.Completed) {
            if (isImageGalleryOutput) {
                return completedGallerySummary ?: "图片已保存到系统相册"
            }
            val savedBytes = (totalBytes ?: downloadedBytes).takeIf { it > 0L }
            return savedBytes?.let { "已保存 · ${formatBytes(it)}" } ?: "已保存到系统媒体库"
        }
        if (stage == DownloadTaskStage.Failed) return "下载未完成，可重试或查看错误原因"
        if (stage == DownloadTaskStage.Merging) return "媒体已下载，正在合并音视频…"
        if (stage == DownloadTaskStage.Resolving) return "正在重新确认媒体地址…"
        if (stage == DownloadTaskStage.Queued) return "等待可用下载槽位…"

        val parts = mutableListOf<String>()
        if (downloadedBytes > 0L || totalBytes != null) {
            val size = if (totalBytes != null) {
                "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
            } else {
                formatBytes(downloadedBytes)
            }
            parts += size
        }
        bytesPerSecond?.takeIf { it > 0 }?.let { parts += "${formatBytes(it)}/s" }
        etaSeconds?.takeIf { it >= 0 }?.let { parts += formatEta(it) }
        if (parts.isEmpty()) parts += stage.label
        return parts.joinToString(" · ")
    }

private fun formatBytes(bytes: Long): String {
    if (bytes < 1_024L) return "$bytes B"
    val kib = bytes / 1_024.0
    if (kib < 1_024.0) return "%.1f KB".format(kib)
    val mib = kib / 1_024.0
    if (mib < 1_024.0) return "%.1f MB".format(mib)
    return "%.2f GB".format(mib / 1_024.0)
}

private fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "约 ${seconds} 秒"
    seconds < 3_600 -> "约 ${seconds / 60} 分钟"
    else -> "约 ${seconds / 3_600} 小时"
}
