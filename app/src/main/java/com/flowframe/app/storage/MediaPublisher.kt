package com.flowframe.app.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object MediaPublisher {
    suspend fun publish(context: Context, source: File, audioOnly: Boolean): String {
        return if (audioOnly) publishAudio(context, source) else publishVideo(context, source)
    }

    suspend fun publishVideo(context: Context, source: File): String =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            source.absolutePath
        } else {
            publishSingle(
                context = context,
                source = source,
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                relativePath = "${Environment.DIRECTORY_MOVIES}/FlowFrame",
                fallbackMimeType = "video/mp4",
            )
        }

    suspend fun publishAudio(context: Context, source: File): String =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            source.absolutePath
        } else {
            publishSingle(
                context = context,
                source = source,
                collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                relativePath = "${Environment.DIRECTORY_MUSIC}/FlowFrame",
                fallbackMimeType = "audio/mpeg",
            )
        }

    /**
     * Publishes a gallery as one all-or-nothing transaction. Entries stay pending
     * until every source has copied successfully; any failure removes the entire set.
     */
    suspend fun publishImages(context: Context, sources: List<File>, folderName: String): List<String> {
        require(sources.isNotEmpty()) { "没有可保存的图片" }
        sources.forEach { require(it.isFile && it.length() > 0L) { "图片暂存文件不完整" } }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return sources.map(File::getAbsolutePath)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_PICTURES}/FlowFrame/${safeFolderName(folderName)}"
        val inserted = mutableListOf<Uri>()
        try {
            sources.forEach { source ->
                currentCoroutineContext().ensureActive()
                val values = pendingValues(
                    source = source,
                    relativePath = relativePath,
                    fallbackMimeType = "image/webp",
                )
                val uri = resolver.insert(collection, values)
                    ?: throw IllegalStateException("无法在系统相册中创建图片")
                inserted += uri
                resolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().use { input -> copyCancelable(input, output) }
                    output.flush()
                } ?: throw IllegalStateException("无法写入系统相册")
            }
            inserted.forEach { uri ->
                currentCoroutineContext().ensureActive()
                val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                require(resolver.update(uri, values, null, null) > 0) { "无法发布系统相册图片" }
            }
            currentCoroutineContext().ensureActive()
            sources.forEach(File::delete)
            return inserted.map(Uri::toString)
        } catch (error: Throwable) {
            inserted.forEach { uri -> runCatching { resolver.delete(uri, null, null) } }
            throw error
        }
    }

    private suspend fun publishSingle(
        context: Context,
        source: File,
        collection: Uri,
        relativePath: String,
        fallbackMimeType: String,
    ): String {
        require(source.isFile && source.length() > 0L) { "媒体暂存文件不完整" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return source.absolutePath

        val values = pendingValues(source, relativePath, fallbackMimeType)

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("无法写入系统媒体库")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> copyCancelable(input, output) }
                output.flush()
            } ?: throw IllegalStateException("无法打开系统媒体库")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            require(resolver.update(uri, values, null, null) > 0) { "无法发布系统媒体文件" }
            currentCoroutineContext().ensureActive()
            source.delete()
            return uri.toString()
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun pendingValues(
        source: File,
        relativePath: String,
        fallbackMimeType: String,
    ) = ContentValues().apply {
        val extension = source.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: fallbackMimeType
        put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    private fun safeFolderName(value: String): String {
        val cleaned = value
            .replace(Regex("""[\\/:*?\"<>|\p{Cntrl}]"""), "_")
            .trim(' ', '.')
            .take(80)
        return cleaned.ifBlank { "图文作品" }
    }

    private suspend fun copyCancelable(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) return
            output.write(buffer, 0, count)
        }
    }
}
