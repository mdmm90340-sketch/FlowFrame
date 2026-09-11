package com.flowframe.app.core.engine

import android.content.Context
import com.flowframe.app.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest

/** Keeps an existing 0.18.1 installation from retaining the old 4 KiB WebP payload. */
internal object BundledNativeInstaller {
    private const val MANIFEST = "open_source/WEBP-NATIVE-MANIFEST.json"
    private val libraryNames = setOf(
        "libsharpyuv.so", "libwebp.so", "libwebpdecoder.so", "libwebpdemux.so", "libwebpmux.so",
    )

    // Called under DownloadEngine's initialization lock, before the first FFmpeg.init.
    fun prepare(context: Context) {
        if (installedLibrariesMatch(context)) return
        val directory = packageDirectory(context)
        check(!directory.exists() || directory.isDirectory && directory.deleteRecursively()) {
            "无法更新音视频组件，请释放存储空间后重试"
        }
    }

    fun verify(context: Context) {
        check(installedLibrariesMatch(context)) { "内置音视频组件完整性校验失败" }
    }

    private fun installedLibrariesMatch(context: Context): Boolean {
        val manifest = context.assets.open(MANIFEST).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val libraries = Json.parseToJsonElement(manifest).jsonObject.getValue("libraries").jsonArray
            .map { it.jsonObject }
            .filter { it.getValue("abi").jsonPrimitive.content == BuildConfig.NATIVE_ABI }
        check(libraries.size == libraryNames.size && libraries.map {
            it.getValue("name").jsonPrimitive.content
        }.toSet() == libraryNames) { "内置音视频组件清单无效" }
        val directory = File(packageDirectory(context), "usr/lib")
        return libraries.all { item ->
            val file = File(directory, item.getValue("name").jsonPrimitive.content)
            file.isFile && runCatching {
                sha256(file) == item.getValue("sha256").jsonPrimitive.content
            }.getOrDefault(false)
        }
    }

    private fun packageDirectory(context: Context) =
        File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")

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
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
