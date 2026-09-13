package com.flowframe.app.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.flowframe.app.R
import com.flowframe.app.ui.components.FlowFrameLogo
import com.flowframe.app.ui.components.StatePanel
import com.flowframe.app.ui.model.FlowFrameOverlay
import com.flowframe.app.ui.model.FlowFrameUiState
import com.flowframe.app.ui.model.MediaPreviewUi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryBrowserScreen(
    state: MediaPreviewUi,
    onBack: () -> Unit,
    onImageSelectionChanged: (Int, Boolean) -> Unit,
    onSelectAllImages: (Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { state.imageUrls.size })
    val scope = rememberCoroutineScope()
    val current = pagerState.currentPage
    val allSelected = state.imageUrls.isNotEmpty() &&
        state.imageUrls.indices.all { it in state.selectedImageIndices }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("选择图片", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "已选 " + state.selectedImageCount + " / " + state.resolvedImageCount + " 张",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回预览")
                    }
                },
                actions = { TextButton(onClick = onBack) { Text("完成") } },
            )
        },
        bottomBar = {
            if (state.imageUrls.isNotEmpty()) Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = current in state.selectedImageIndices,
                            onCheckedChange = { onImageSelectionChanged(current, it) },
                            modifier = Modifier.testTag("gallery_current_selection").semantics {
                                contentDescription = "保存第 " + (current + 1) + " 张图片"
                            },
                        )
                        Text(
                            "保存第 " + (current + 1) + " 张",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onSelectAllImages(!allSelected) }, modifier = Modifier.testTag("gallery_select_all")) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.imageUrls.indices.toList(), key = { it }) { index ->
                            Surface(
                                modifier = Modifier
                                    .size(width = 64.dp, height = 78.dp)
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "查看第 " + (index + 1) + " 张",
                                    ) { scope.launch { pagerState.animateScrollToPage(index) } }
                                    .semantics {
                                        stateDescription = if (index in state.selectedImageIndices) "已选中保存" else "未选中保存"
                                    },
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(
                                    if (index == current) 2.dp else 1.dp,
                                    if (index == current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                Box {
                                    AsyncImage(
                                        model = state.imageUrls[index],
                                        contentDescription = "第 " + (index + 1) + " 张缩略图",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                        color = if (index in state.selectedImageIndices) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    ) {
                                        Text(
                                            (index + 1).toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (state.imageUrls.isEmpty()) Box(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            StatePanel(
                icon = Icons.Rounded.HideImage,
                title = "图片地址暂不可用",
                message = "返回后重新解析作品，再查看图片。",
                actionLabel = "返回预览",
                onAction = onBack,
            )
        } else Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = (current + 1).toString() + " / " + state.imageUrls.size + " · 左右滑动查看原图",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1,
            ) { page ->
                SubcomposeAsyncImage(
                    model = state.imageUrls[page],
                    contentDescription = "第 " + (page + 1) + " 张图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    } },
                    error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.HideImage, contentDescription = null, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("这张图片暂不可用", style = MaterialTheme.typography.bodyMedium)
                        }
                    } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformationScreen(
    overlay: FlowFrameOverlay,
    uiState: FlowFrameUiState,
    onBack: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onOpenRepository: () -> Unit,
) {
    val appName = stringResource(R.string.app_name)
    val title = when (overlay) {
        FlowFrameOverlay.FormatDetails -> "格式详情"
        FlowFrameOverlay.Diagnostics -> "诊断信息"
        FlowFrameOverlay.About -> stringResource(R.string.about_app, appName)
        FlowFrameOverlay.Gallery -> "图片浏览"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 800.dp).fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (overlay) {
                    FlowFrameOverlay.FormatDetails -> {
                        item { InformationCard(
                            "本次解析",
                            uiState.activePreview?.formatDetails?.takeIf(String::isNotBlank)
                                ?: "当前解析器没有提供额外格式信息。可返回预览页选择保存质量。",
                        ) }
                        item { InformationCard(
                            "保存质量如何选择",
                            "推荐：优先匹配最高 1080P 与常见编码。\n\n最高画质：保留当前作品可获取的最高质量，文件可能更大。\n\n节省空间：优先匹配最高 720P。\n\n仅音频：提取可用音轨。最终质量取决于平台实际提供的流。",
                        ) }
                    }
                    FlowFrameOverlay.Diagnostics -> {
                        item { Text(
                            "复制后可用于反馈问题。分享前请检查内容。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        ) }
                        item { InformationCard(
                            "设备与运行状态",
                            uiState.diagnosticsText.ifBlank { "暂时没有诊断信息" },
                            monospace = true,
                        ) }
                        item { Button(
                            onClick = onCopyDiagnostics,
                            enabled = uiState.diagnosticsText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("复制诊断信息", Modifier.padding(start = 8.dp))
                        } }
                    }
                    FlowFrameOverlay.About -> {
                        item { Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            FlowFrameLogo()
                            Column {
                                Text(appName, style = MaterialTheme.typography.headlineSmall)
                                Text("版本 " + uiState.settings.versionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } }
                        item { InformationCard("视频与图集下载", "解析作品链接，选择保存质量与内容，并在后台完成下载。任务完成后可打开或分享文件。") }
                        item { InformationCard(
                            "隐私说明",
                            "仅在你点击粘贴时读取剪贴板文字。解析与下载会访问作品所在平台及其媒体地址。任务记录、保存设置与输出文件保存在设备上。\n\n${appName}不要求账号登录，也不会读取其他应用的登录凭据。删除任务记录不会删除已经保存的文件。",
                        ) }
                        item { InformationCard(
                            "开源许可",
                            "$appName 按 GNU GPL v3 许可发布。完整源代码、许可文本和依赖说明可在项目仓库查看。\n\n请仅保存你有权下载和使用的内容。",
                        ) }
                        item { OutlinedButton(onClick = onOpenRepository, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("打开项目与开源许可", Modifier.padding(start = 8.dp))
                        } }
                    }
                    FlowFrameOverlay.Gallery -> item { InformationCard("预览已关闭", "请重新解析作品以查看图片。") }
                }
            }
        }
    }
}

@Composable
private fun InformationCard(title: String, text: String, monospace: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
