package com.flowframe.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowframe.app.ui.components.MediaArtwork
import com.flowframe.app.ui.components.PlatformBadge
import com.flowframe.app.ui.model.GalleryOutputModeUi
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
        topBar = {
            TopAppBar(
                title = { Text("下载预览", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回首页")
                    }
                },
            )
        },
        bottomBar = { PreviewActionBar(state, onDownloadRequested) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            BoxWithConstraints(Modifier.widthIn(max = 1120.dp).fillMaxSize()) {
                val wide = maxWidth >= 760.dp
                if (wide) Row(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) { PreviewHeader(state) }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        PreviewOptions(
                            state, onPresetSelected, onGalleryOutputModeSelected,
                            onAudioOnlyChanged, onContentSelectionRequested, onFormatDetailsRequested,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                } else Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    PreviewHeader(state)
                    PreviewOptions(
                        state, onPresetSelected, onGalleryOutputModeSelected,
                        onAudioOnlyChanged, onContentSelectionRequested, onFormatDetailsRequested,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(state: MediaPreviewUi) {
    Box {
        MediaArtwork(
            platform = state.platform,
            title = state.title,
            thumbnailUrl = state.thumbnailUrl ?: state.imageUrls.firstOrNull(),
            showTitle = false,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.6f),
        )
        PlatformBadge(state.platform, Modifier.align(Alignment.TopStart).padding(12.dp))
        if (state.previewBadgeLabel.isNotBlank()) Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Text(
                state.previewBadgeLabel,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            state.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            state.author,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.displayMetadataLabel.isNotBlank()) Text(
            state.displayMetadataLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.description.isNotBlank()) Text(
            state.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewOptions(
    state: MediaPreviewUi,
    onPresetSelected: (String) -> Unit,
    onGalleryOutputModeSelected: (GalleryOutputModeUi) -> Unit,
    onAudioOnlyChanged: (Boolean) -> Unit,
    onContentSelectionRequested: () -> Unit,
    onFormatDetailsRequested: () -> Unit,
) {
    if (state.mediaKind == MediaKindUi.Gallery) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("preview_gallery").clickable(
                role = Role.Button,
                onClickLabel = "浏览并选择要保存的图片",
                onClick = onContentSelectionRequested,
            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Collections, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("浏览与选择图片", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已选 " + state.selectedImageCount + " / " + state.resolvedImageCount + " 张",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (state.mediaKind == MediaKindUi.Gallery) "保存方式" else "保存质量",
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        TextButton(onClick = onFormatDetailsRequested) {
            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(17.dp))
            Text("格式详情", Modifier.padding(start = 6.dp))
        }
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.mediaKind == MediaKindUi.Gallery) {
            state.galleryOutputOptions.forEach { option ->
                OutputChoice(
                    title = option.title,
                    subtitle = option.subtitle,
                    detail = if (option.available) option.detail else option.unavailableReason.orEmpty(),
                    selected = state.selectedGalleryOutputMode == option.mode,
                    available = option.available,
                    onClick = { onGalleryOutputModeSelected(option.mode) },
                )
            }
        } else {
            state.presets.forEach { preset ->
                OutputChoice(
                    title = preset.title,
                    subtitle = preset.subtitle,
                    detail = if (preset.available) preset.detail else preset.unavailableReason.orEmpty(),
                    selected = !state.audioOnly && state.selectedPresetId == preset.id,
                    available = preset.available,
                    onClick = { onPresetSelected(preset.id) },
                )
            }
            if (state.presets.isEmpty()) Text(
                "没有找到可下载的格式，请返回重新解析。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            FilterChip(
                selected = state.audioOnly,
                onClick = { onAudioOnlyChanged(!state.audioOnly) },
                enabled = state.hasAudio,
                label = { Text(if (state.hasAudio) "仅保存音频" else "没有可用音轨") },
                leadingIcon = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp)) },
            )
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Text(
                when {
                    state.mediaKind == MediaKindUi.Video && state.audioOnly -> "保存音频轨道，完成后可打开或分享。"
                    state.mediaKind == MediaKindUi.Video -> "必要时自动合并音视频，文件处理完成后即可打开。"
                    state.selectedGalleryOutputMode == GalleryOutputModeUi.Audio -> "仅保存作品配乐，图片不会写入保存目录。"
                    state.selectedImageCount == 0 -> "还没有选择图片。请先选择至少一张图片。"
                    state.selectedGalleryOutputMode == GalleryOutputModeUi.Images -> "按原顺序保存已选的 " + state.selectedImageCount + " 张图片。"
                    else -> "将选中的 " + state.selectedImageCount + " 张图片合成 MP4" +
                        if (state.hasAudio) "，并保留配乐。" else "，不添加音轨。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun OutputChoice(
    title: String,
    subtitle: String,
    detail: String,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(
            selected = selected,
            enabled = available,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = available)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                if (detail.isNotBlank()) Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewActionBar(state: MediaPreviewUi, onDownloadRequested: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 800.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.destinationLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.estimatedSizeLabel?.let { Text("约 " + it, style = MaterialTheme.typography.labelMedium) }
                }
                Button(
                    onClick = onDownloadRequested,
                    enabled = state.canDownload && state.selectedOutputAvailable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("preview_download"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(20.dp))
                    Text(
                        when {
                            state.mediaKind == MediaKindUi.Video && state.audioOnly -> "下载音频"
                            state.mediaKind == MediaKindUi.Video -> "开始下载"
                            state.selectedGalleryOutputMode == GalleryOutputModeUi.Images -> "保存图片"
                            state.selectedGalleryOutputMode == GalleryOutputModeUi.Audio -> "保存配乐"
                            else -> "生成 MP4"
                        },
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
