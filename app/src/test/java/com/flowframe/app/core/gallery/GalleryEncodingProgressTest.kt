package com.flowframe.app.core.gallery

import org.junit.Assert.*
import org.junit.Test

class GalleryEncodingProgressTest {
    @Test fun usesReportedEncodingTimeIncludingFfmpegLegacyMicroseconds() {
        assertEquals(500_000L, GalleryEncodingProgress.timeMicros("out_time_us=500000"))
        assertEquals(500_000L, GalleryEncodingProgress.timeMicros("out_time_ms=500000"))
        assertEquals(0.25f, GalleryEncodingProgress.fraction(500_000L, 2_000L), 0.0001f)
        assertEquals(1f, GalleryEncodingProgress.fraction(4_000_000L, 2_000L), 0f)
        assertEquals(0f, GalleryEncodingProgress.fraction(500_000L, 0L), 0f)
        assertNull(GalleryEncodingProgress.timeMicros("total_size=524288"))
        assertNull(GalleryEncodingProgress.timeMicros("out_time_us=N/A"))
    }
}
