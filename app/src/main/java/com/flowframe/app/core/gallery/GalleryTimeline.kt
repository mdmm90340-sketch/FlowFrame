package com.flowframe.app.core.gallery

import kotlin.math.abs

object GalleryTimeline {
    const val DEFAULT_IMAGE_DURATION_MILLIS = 3_000L

    fun outputDurationMillis(
        audioDurationMillis: Long?,
        hasDownloadedAudio: Boolean,
        imageCount: Int,
    ): Long {
        require(imageCount > 0) { "图文至少需要一张图片" }
        return if (hasDownloadedAudio) {
            audioDurationMillis?.takeIf { it > 0L }
                ?: DEFAULT_IMAGE_DURATION_MILLIS * imageCount
        } else {
            DEFAULT_IMAGE_DURATION_MILLIS * imageCount
        }
    }

    fun transitionBoundariesMillis(
        beats: List<Long>,
        durationMillis: Long,
        imageCount: Int,
        maxBeatDistanceMillis: Long = 1_500L,
    ): List<Long> {
        require(durationMillis > 0L) { "图文时长必须大于零" }
        require(imageCount > 0) { "图文至少需要一张图片" }
        if (imageCount == 1) return emptyList()
        val validBeats = beats.filter { it in 500L until durationMillis }.distinct().sorted()
        return (1 until imageCount).map { index ->
            val target = durationMillis * index / imageCount
            validBeats.minByOrNull { abs(it - target) }
                ?.takeIf { abs(it - target) <= maxBeatDistanceMillis }
                ?: target
        }
    }
}
