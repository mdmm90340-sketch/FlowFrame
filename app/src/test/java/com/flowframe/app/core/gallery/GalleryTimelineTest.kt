package com.flowframe.app.core.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryTimelineTest {
    @Test
    fun usesAudioDurationAndNearestBeatForEachImageBoundary() {
        val duration = GalleryTimeline.outputDurationMillis(
            audioDurationMillis = 51_000L,
            hasDownloadedAudio = true,
            imageCount = 5,
        )
        val boundaries = GalleryTimeline.transitionBoundariesMillis(
            beats = listOf(270L, 10_060L, 20_300L, 30_520L, 40_750L, 50_960L),
            durationMillis = duration,
            imageCount = 5,
        )

        assertEquals(51_000L, duration)
        assertEquals(listOf(10_060L, 20_300L, 30_520L, 40_750L), boundaries)
    }

    @Test
    fun missingAudioUsesThreeSecondsPerImageAndEqualBoundaries() {
        val duration = GalleryTimeline.outputDurationMillis(
            audioDurationMillis = null,
            hasDownloadedAudio = false,
            imageCount = 5,
        )

        assertEquals(15_000L, duration)
        assertEquals(
            listOf(3_000L, 6_000L, 9_000L, 12_000L),
            GalleryTimeline.transitionBoundariesMillis(emptyList(), duration, 5),
        )
    }

    @Test
    fun ignoresBeatThatIsTooFarFromEqualPartition() {
        assertEquals(
            listOf(5_000L),
            GalleryTimeline.transitionBoundariesMillis(listOf(1_000L), 10_000L, 2),
        )
    }
}
