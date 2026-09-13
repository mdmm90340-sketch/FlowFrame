package com.flowframe.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowframe.app.ui.model.MediaPlatform
import com.flowframe.app.R
import com.flowframe.app.ui.theme.BilibiliAccent
import com.flowframe.app.ui.theme.DouyinAccent

@Composable
fun FlowFrameBrandHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlowFrameLogo(Modifier.size(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun FlowFrameLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_brand),
        contentDescription = null,
        modifier = modifier.size(48.dp),
    )
}

@Composable
fun PlatformBadge(
    platform: MediaPlatform,
    modifier: Modifier = Modifier,
) {
    val accent = platformAccent(platform)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = accent.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(platform.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun MediaArtwork(
    platform: MediaPlatform,
    title: String,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    showTitle: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val accent = platformAccent(platform)
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        accent.copy(alpha = 0.72f),
                    ),
                ),
            ),
    ) {
        Icon(
            imageVector = Icons.Rounded.VideoLibrary,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(58.dp),
            tint = Color.White.copy(alpha = 0.9f),
        )
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "$title 的封面",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showTitle) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun StatePanel(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    isError: Boolean = false,
    onAction: () -> Unit = {},
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.1f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(12.dp).size(28.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.78f),
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth().semantics { heading() }) {
        if (eyebrow != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = eyebrow,
                    modifier = Modifier.padding(start = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

val MediaPlatform.label: String
    get() = when (this) {
        MediaPlatform.Douyin -> "抖音"
        MediaPlatform.Bilibili -> "哔哩哔哩"
        MediaPlatform.Xiaohongshu -> "小红书"
        MediaPlatform.Weibo -> "微博"
        MediaPlatform.Kuaishou -> "快手"
        MediaPlatform.Unknown -> "未知来源"
    }

@Composable
fun platformAccent(platform: MediaPlatform): Color = when (platform) {
    MediaPlatform.Douyin -> DouyinAccent
    MediaPlatform.Bilibili -> BilibiliAccent
    MediaPlatform.Xiaohongshu -> Color(0xFFE34C69)
    MediaPlatform.Weibo -> Color(0xFFD98620)
    MediaPlatform.Kuaishou -> Color(0xFFE7793F)
    MediaPlatform.Unknown -> MaterialTheme.colorScheme.primary
}
