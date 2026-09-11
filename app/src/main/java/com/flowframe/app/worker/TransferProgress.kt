package com.flowframe.app.worker

data class TransferProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long?,
    val etaSeconds: Long?,
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }

    companion object {
        const val PREFIX = "FLOWFRAME_PROGRESS:"
        fun parse(line: String): TransferProgress? {
            if (!line.contains(PREFIX)) return null
            val values = line.substringAfter(PREFIX).trim().split('|')
            if (values.size != 5) return null
            fun number(index: Int) = values[index].toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0 && it <= Long.MAX_VALUE.toDouble() }?.toLong()
            val downloaded = number(0) ?: return null
            return TransferProgress(downloaded, number(1) ?: number(2), number(3), number(4))
        }
    }
}
