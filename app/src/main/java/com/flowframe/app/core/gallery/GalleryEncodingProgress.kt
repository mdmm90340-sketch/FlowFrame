package com.flowframe.app.core.gallery

/** FFmpeg's out_time_us and legacy out_time_ms both contain microseconds. */
object GalleryEncodingProgress {
    fun timeMicros(line: String): Long? = when (line.substringBefore('=')) {
        "out_time_us", "out_time_ms" -> line.substringAfter('=').toLongOrNull()?.coerceAtLeast(0L)
        else -> null
    }

    fun fraction(encodedMicros: Long, durationMillis: Long): Float =
        if (durationMillis <= 0L) 0f
        else (encodedMicros.toDouble() / (durationMillis.toDouble() * 1_000.0)).toFloat().coerceIn(0f, 1f)
}
