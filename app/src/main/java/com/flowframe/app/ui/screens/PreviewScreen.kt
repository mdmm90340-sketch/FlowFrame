package com.flowframe.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowframe.app.ui.components.MediaArtwork
import com.flowframe.app.ui.components.PlatformBadge
import com.flowframe.app.ui.components.SectionHeading
import com.flowframe.app.ui.model.FormatPresetUi
import com.flowframe.app.ui.model.GalleryOutputModeUi
import com.flowframe.app.ui.model.GalleryOutputOptionUi
import com.flowframe.app.ui.model.MediaKindUi
import com.flowframe.app.ui.model.MediaPreviewUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    state: MediaPreviewUi,
    onBack: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onGalleryOutputModeSelected: (GalleryOutputModeUi) -> Unit,
    onAudioOnlyChanged: (Boolean) -> Unit,
    onContentSelectionRequested: () -> Unit,
    onFormatDetailsRequested: () -> Unit,
    onDownloadRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("解析预览", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "确认内容与保存格式",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            PreviewActionBar(
                destinationLabel = state.destinationLabel,
                estimatedSizeLabel = state.estimatedSizeLabel,
                actionLabel = state.downloadActionLabel,
                canDownload = state.canDownload && state.selectedOutputAvailable,
                onDownloadRequested = onDownloadRequested,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Box {
                    MediaArtwork(
                        platform = state.platform,
                        title = state.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(216.dp),
                    )
                    PlatformBadge(
                        platform = state.platform,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                    )
                    if (state.previewBadgeLabel.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
                        ) {
                            Text(
                                text = state.previewBadgeLabel,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = buildString {
                            append(state.author)
                            if (state.displayMetadataLabel.isNotBlank()) {
                                append(" · ${state.displayMetadataLabel}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.description.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = state.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            val selectableContentCount = when (state.mediaKind) {
                MediaKindUi.Video -> state.contentCount
                MediaKindUi.Gallery -> state.resolvedImageCount
            }
            if (selectableContentCount > 1) {
                item {
                    ContentSelectionCard(
                        contentCount = selectableContentCount,
                        selectedCount = state.selectedContentCount,
                        mediaKind = state.mediaKind,
                        onClick = onContentSelectionRequested,
                    )
                }
            }

            if (state.mediaKind == MediaKindUi.Gallery) {
                item {
                    SectionHeading(
                        eyebrow = "图文作品",
                        title = "选择保存方式",
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.galleryOutputOptions, key = { it.mode.name }) { option ->
                            GalleryOutputModeCard(
                                option = option,
                                selected = option.mode == state.selectedGalleryOutputMode,
                                onClick = { onGalleryOutputModeSelected(option.mode) },
                            )
                        }
                    }
                }
            } else {
                item {
                    SectionHeading(
                        eyebrow = "智能匹配",
                        title = "选择保存质量",
                    )
                }

                if (state.presets.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Text(
                                text = "没有找到可下载的格式，请重新解析链接。",
                                modifier = Modifier.padding(18.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                } else {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.presets, key = { it.id }) { preset ->
                                FormatPresetCard(
                                    preset = preset,
                                    selected = preset.id == state.selectedPresetId,
                                    onClick = { onPresetSelected(preset.id) },
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilterChip(
                            selected = state.audioOnly,
                            onClick = { onAudioOnlyChanged(!state.audioOnly) },
                            label = { Text("仅音频") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        OutlinedButton(
                            onClick = onFormatDetailsRequested,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("格式说明", modifier = Modifier.padding(start = 7.dp))
                        }
                    }
                }
            }

            item {
                OutputSummaryCard(state)
            }
        }
    }
}

@Composable
private fun ContentSelectionCard(
    contentCount: Int,
    selectedCount: Int,
    mediaKind: MediaKindUi,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = if (mediaKind == MediaKindUi.Gallery) {
                        Icons.Rounded.Collections
                    } else {
                        Icons.AutoMirrored.Rounded.PlaylistPlay
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (mediaKind == MediaKindUi.Gallery) "查看图片" else "选择内容",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (mediaKind == MediaKindUi.Gallery) {
                        "共 $contentCount 张 · 将全部保存"
                    } else {
                        "共 $contentCount 项 · 已选 $selectedCount 项"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = if (mediaKind == MediaKindUi.Gallery) "查看图片" else "选择分集",
            )
        }
    }
}

@Composable
private fun FormatPresetCard(
    preset: FormatPresetUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = when {
        selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Card(
        modifier = Modifier
            .width(178.dp)
            .animateContentSize()
            .clickable(enabled = preset.available, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (preset.available) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
            },
        ),
        border = border,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = preset.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!preset.available) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "当前不可用",
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            preset.badge?.let { badge ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preset.unavailableReason ?: preset.detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GalleryOutputModeCard(
    option: GalleryOutputOptionUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon = when (option.mode) {
        GalleryOutputModeUi.Images -> Icons.Rounded.PhotoLibrary
        GalleryOutputModeUi.Audio -> Icons.Rounded.AudioFile
        GalleryOutputModeUi.Mp4 -> Icons.Rounded.MovieCreation
    }
    Card(
        modifier = Modifier
            .width(190.dp)
            .animateContentSize()
            .clickable(enabled = option.available, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (option.available) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
            },
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    text = option.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!option.available) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "当前不可用",
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            option.badge?.let { badge ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = option.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (option.available) option.detail else option.unavailableReason.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OutputSummaryCard(state: MediaPreviewUi) {
    val summary = when (state.mediaKind) {
        MediaKindUi.Video -> if (state.audioOnly) {
            Triple(
                Icons.Rounded.AudioFile,
                "将只保存音频轨道",
                "适合离线聆听；最终格式取决于平台提供的音频流。",
            )
        } else {
            Triple(
                Icons.Rounded.HighQuality,
                "将优先使用兼容格式",
                "必要时会在下载后合并音视频，完成前不会提前显示 100%。",
            )
        }

        MediaKindUi.Gallery -> when (state.selectedGalleryOutputMode) {
            GalleryOutputModeUi.Images -> Triple(
                Icons.Rounded.PhotoLibrary,
                "将逐张保存 ${state.resolvedImageCount} 张图片",
                "每张图片都会独立进入系统相册，任务完成后可打开首张或一次分享全部。",
            )

            GalleryOutputModeUi.Audio -> Triple(
                Icons.Rounded.AudioFile,
                if (state.hasAudio) "将只保存图集配乐" else "当前作品没有可用配乐",
                if (state.hasAudio) {
                    "保留作品原声，图片不会写入相册。"
                } else {
                    "请选择保存图片或合成 MP4。"
                },
            )

            GalleryOutputModeUi.Mp4 -> Triple(
                Icons.Rounded.MovieCreation,
                "将自动合成为 MP4",
                if (state.hasAudio) {
                    "按作品顺序与节奏生成视频，并保留原配乐。"
                } else {
                    "按作品顺序生成静音视频，便于播放和分享。"
                },
            )
        }
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Icon(
                imageVector = summary.first,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.second,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = summary.third,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val MediaPreviewUi.downloadActionLabel: String
    get() = when (mediaKind) {
        MediaKindUi.Video -> "下载"
        MediaKindUi.Gallery -> when (selectedGalleryOutputMode) {
            GalleryOutputModeUi.Images -> "保存图片"
            GalleryOutputModeUi.Audio -> "保存音频"
            GalleryOutputModeUi.Mp4 -> "生成 MP4"
        }
    }

@Composable
private fun PreviewActionBar(
    destinationLabel: String,
    estimatedSizeLabel: String?,
    actionLabel: String,
    canDownload: Boolean,
    onDownloadRequested: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = destinationLabel,
                        modifier = Modifier.padding(start = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (estimatedSizeLabel != null) {
                    Text(
                        text = "预计 $estimatedSizeLabel",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Button(
                onClick = onDownloadRequested,
                enabled = canDownload,
                contentPadding = PaddingValues(horizontal = 19.dp, vertical = 13.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
                Text(actionLabel, modifier = Modifier.padding(start = 7.dp))
            }
        }
    }
}
