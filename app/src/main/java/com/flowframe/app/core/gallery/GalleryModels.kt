package com.flowframe.app.core.gallery

data class GalleryImageSource(
    val width: Int,
    val height: Int,
    val mirrors: List<String>,
)

data class GalleryAudioSource(
    val mirrors: List<String>,
    val durationMillis: Long,
    val title: String? = null,
    val artist: String? = null,
)

data class GalleryAssetSet(
    val sourceUrl: String,
    val mediaId: String,
    val title: String,
    val uploader: String?,
    val images: List<GalleryImageSource>,
    val audio: GalleryAudioSource?,
    val beatTimesMillis: List<Long>,
)

data class DownloadedGalleryAssets(
    val images: List<java.io.File>,
    val audio: java.io.File?,
)
