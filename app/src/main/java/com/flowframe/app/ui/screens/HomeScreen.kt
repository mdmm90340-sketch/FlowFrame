package com.flowframe.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowframe.app.ui.components.FlowFrameBrandHeader
import com.flowframe.app.ui.components.PlatformBadge
import com.flowframe.app.ui.components.StatePanel
import com.flowframe.app.ui.components.label
import com.flowframe.app.ui.model.HomeUiState
import com.flowframe.app.ui.model.ParseStage
import com.flowframe.app.ui.model.ParseUiState
import com.flowframe.app.ui.model.RecentMediaUi

@Composable
fun HomeScreen(
    state: HomeUiState,
    onLinkChanged: (String) -> Unit,
    onPasteRequested: () -> Unit,
    onClearRequested: () -> Unit,
    onParseRequested: () -> Unit,
    onErrorAction: () -> Unit,
    onRecentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsing = state.parseState is ParseUiState.Loading
    val canParse = state.canParse && state.linkText.isNotBlank() && !parsing

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 22.dp,
            end = 20.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            FlowFrameBrandHeader(
                title = "链接一贴",
                subtitle = "清晰落地",
            )
        }

        item {
            Column {
                Text(
                    text = "保存此刻，保持原本的清晰。",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "支持抖音视频与图文分享，以及哔哩哔哩视频地址。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            LinkComposer(
                state = state,
                canParse = canParse,
                parsing = parsing,
                onLinkChanged = onLinkChanged,
                onPasteRequested = onPasteRequested,
                onClearRequested = onClearRequested,
                onParseRequested = onParseRequested,
            )
        }

        item {
            AnimatedContent(
                targetState = state.parseState,
                modifier = Modifier.animateContentSize(),
                label = "parse_state",
            ) { parseState ->
                when (parseState) {
                    ParseUiState.Idle -> SupportedSourcesCard()
                    is ParseUiState.Loading -> ParseLoadingCard(parseState.stage)
                    is ParseUiState.Error -> StatePanel(
                        icon = Icons.Rounded.ErrorOutline,
                        title = parseState.title,
                        message = parseState.message,
                        actionLabel = parseState.actionLabel,
                        isError = true,
                        onAction = onErrorAction,
                    )
                }
            }
        }

        if (state.recentItems.isNotEmpty()) {
            item {
                Text(
                    text = "最近解析",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(
                items = state.recentItems.take(3),
                key = { it.id },
            ) { item ->
                RecentMediaCard(
                    item = item,
                    onClick = { onRecentSelected(item.id) },
                )
            }
        }
    }
}

@Composable
private fun LinkComposer(
    state: HomeUiState,
    canParse: Boolean,
    parsing: Boolean,
    onLinkChanged: (String) -> Unit,
    onPasteRequested: () -> Unit,
    onClearRequested: () -> Unit,
    onParseRequested: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "作品链接",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                AnimatedVisibility(
                    visible = state.detectedPlatform != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    state.detectedPlatform?.let { PlatformBadge(it) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = state.linkText,
                    onValueChange = onLinkChanged,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    enabled = !parsing,
                    isError = state.inputMessage != null && state.inputMessageIsError,
                    placeholder = {
                        Text("粘贴分享文案或 https:// 链接")
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Link, contentDescription = null)
                    },
                    trailingIcon = {
                        if (state.linkText.isNotBlank()) {
                            IconButton(onClick = onClearRequested) {
                                Icon(Icons.Rounded.Clear, contentDescription = "清空链接")
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { if (canParse) onParseRequested() },
                    ),
                )
                AnimatedVisibility(
                    visible = state.inputMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    state.inputMessage?.let { message ->
                        val messageColor = if (state.inputMessageIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (state.inputMessageIsError) {
                                    Icons.Rounded.ErrorOutline
                                } else {
                                    Icons.Rounded.CheckCircle
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = messageColor,
                            )
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelMedium,
                                color = messageColor,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onPasteRequested,
                    enabled = state.canPaste && !parsing,
                    modifier = Modifier.weight(0.9f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("粘贴", modifier = Modifier.padding(start = 7.dp))
                }
                Button(
                    onClick = onParseRequested,
                    enabled = canParse,
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (parsing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = if (parsing) "解析中" else "解析链接",
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportedSourcesCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.padding(11.dp).size(25.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("两种平台，一个清爽流程", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "自动识别来源，解析后再由你选择清晰度与保存方式。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ParseLoadingCard(stage: ParseStage) {
    val (title, message) = when (stage) {
        ParseStage.Recognizing -> "正在识别链接" to "检查分享文本和作品来源…"
        ParseStage.FetchingMetadata -> "正在获取作品信息" to "读取标题、封面和内容列表…"
        ParseStage.ResolvingFormats -> "正在整理可用格式" to "匹配清晰度、音频与兼容方案…"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.secondary,
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentMediaCard(
    item: RecentMediaUi,
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(
                    modifier = Modifier.size(58.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${item.platform.label} · ${item.author} · ${item.metadata}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "查看解析结果",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
