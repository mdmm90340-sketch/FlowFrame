package com.flowframe.app.core.engine

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal object BundledYtDlpInstaller {
    const val KERNEL_VERSION = "2026.08.19-flowframe.douyin.73d89b8.3"
    const val KERNEL_SHA256 = "E88DBA1F25813F43E9292B676F8D03186757CA0D2011DF4CE54ECD60810019E8"

    private const val ENGINE_DIRECTORY = "youtubedl-android"
    private const val YTDLP_DIRECTORY = "yt-dlp"
    private const val YTDLP_FILE = "yt-dlp"
    private const val MARKER_FILE = "flowframe-ytdlp.marker"
    private const val MARKER_TEMP_FILE = "flowframe-ytdlp.marker.tmp"
    private const val MAX_MARKER_BYTES = 1_024L

    private val expectedMarker = buildString {
        append("version=")
        append(KERNEL_VERSION)
        append('\n')
        append("sha256=")
        append(KERNEL_SHA256)
        append('\n')
    }

    /**
     * youtubedl-android copies its raw resource only when this file is absent. Run this before
     * YoutubeDL.init so an app upgrade cannot retain the library's older cached executable.
     */
    fun prepareForLibraryInit(context: Context) {
        val target = targetFile(context)
        val marker = markerFile(context)
        val markerMatches = marker.isFile &&
            marker.length() <= MAX_MARKER_BYTES &&
            runCatching { marker.readText(Charsets.UTF_8) == expectedMarker }.getOrDefault(false)
        val targetMatches = markerMatches && target.isFile &&
            runCatching { sha256(target) == KERNEL_SHA256 }.getOrDefault(false)

        if (!targetMatches && target.exists()) {
            check(target.isFile && target.delete()) {
                "无法替换旧版解析内核"
            }
        }
    }

    /** Write the version marker only after youtubedl-android has copied and initialized the file. */
    fun verifyAndMark(context: Context) {
        val target = targetFile(context)
        check(target.isFile) { "内置解析内核未正确安装" }
        check(sha256(target) == KERNEL_SHA256) { "内置解析内核完整性校验失败" }

        val marker = markerFile(context)
        check(marker.parentFile?.isDirectory == true || marker.parentFile?.mkdirs() == true) {
            "无法创建解析内核目录"
        }
        val temporary = File(marker.parentFile, MARKER_TEMP_FILE)
        if (temporary.exists()) {
            check(temporary.isFile && temporary.delete()) { "无法更新解析内核标记" }
        }
        FileOutputStream(temporary).use { output ->
            output.write(expectedMarker.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Os.rename(temporary.absolutePath, marker.absolutePath)
    }

    private fun targetFile(context: Context): File = File(
        context.noBackupFilesDir,
        "$ENGINE_DIRECTORY/$YTDLP_DIRECTORY/$YTDLP_FILE",
    )

    private fun markerFile(context: Context): File = File(
        context.noBackupFilesDir,
        "$ENGINE_DIRECTORY/$YTDLP_DIRECTORY/$MARKER_FILE",
    )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02X".format(byte) }
    }
}
