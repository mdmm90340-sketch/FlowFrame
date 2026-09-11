package com.flowframe.app.core.gallery

/** Identify bytes, never a CDN filename or a server-provided MIME label. */
enum class ImageMediaType(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp");

    companion object {
        fun fromHeader(bytes: ByteArray): ImageMediaType? {
            fun startsWith(signature: ByteArray) = bytes.size >= signature.size && signature.indices.all { bytes[it] == signature[it] }
            if (startsWith(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))) return JPEG
            if (startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))) return PNG
            if (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") return WEBP
            return null
        }
    }
}
