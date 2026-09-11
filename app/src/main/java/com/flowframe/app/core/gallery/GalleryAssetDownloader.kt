package com.flowframe.app.core.gallery

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.cancellation.CancellationException

class GalleryAssetDownloader {
    suspend fun downloadImages(
        gallery: GalleryAssetSet,
        directory: File,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<File> = withContext(Dispatchers.IO) {
        require(gallery.images.isNotEmpty()) { "图文作品没有可下载的图片" }
        prepareEmptyDirectory(directory)
        val results = mutableListOf<File>()
        try {
            gallery.images.forEachIndexed { index, source ->
                currentCoroutineContext().ensureActive()
                val temporary = File(directory, "%03d.image".format(index + 1))
                downloadFromMirrors(source.mirrors, temporary, MAX_IMAGE_BYTES, gallery.referer) { validateImage(it) }
                currentCoroutineContext().ensureActive()
                val type = imageType(temporary)
                val output = File(directory, "%03d.%s".format(index + 1, type.extension))
                check(temporary.renameTo(output)) { "无法保存图文图片" }
                results += output
                onProgress(index + 1, gallery.images.size)
            }
            results
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    suspend fun downloadAudio(
        audio: GalleryAudioSource,
        directory: File,
        fileName: String = "audio.mp3",
    ): File = withContext(Dispatchers.IO) {
        require(audio.mirrors.isNotEmpty()) { "图文作品没有可下载的背景音乐" }
        require(directory.exists() || directory.mkdirs()) { "无法创建图文暂存目录" }
        val output = File(directory, fileName)
        try {
            downloadFromMirrors(audio.mirrors, output, MAX_AUDIO_BYTES, audio.referer) { validateAudio(it) }
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    private suspend fun downloadFromMirrors(
        mirrors: List<String>,
        destination: File,
        maxBytes: Long,
        referer: String,
        validate: (File) -> Unit,
    ) {
        var lastFailure: Throwable? = null
        for (mirror in mirrors.distinct()) {
            currentCoroutineContext().ensureActive()
            val partial = File(destination.parentFile, "${destination.name}.part")
            partial.delete()
            try {
                downloadOne(mirror, partial, maxBytes, referer)
                currentCoroutineContext().ensureActive()
                validate(partial)
                if (!partial.renameTo(destination)) {
                    partial.copyTo(destination, overwrite = true)
                    partial.delete()
                }
                return
            } catch (error: Throwable) {
                partial.delete()
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                lastFailure = error
            }
        }
        throw IllegalStateException("所有媒体镜像都暂时不可用", lastFailure)
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun downloadOne(rawUrl: String, destination: File, maxBytes: Long, referer: String) {
        var current = RemoteMediaUrls.validate(rawUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            currentCoroutineContext().ensureActive()
            val connection = (current.toURL().openConnection() as? HttpsURLConnection)
                ?: throw IllegalArgumentException("媒体地址不是安全的 HTTPS 地址")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Referer", referer)
            connection.setRequestProperty("Accept", "image/webp,image/png,image/jpeg,audio/mpeg,*/*;q=0.8")
            val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
                if (cause != null) connection.disconnect()
            }
            try {
                val response = connection.responseCode
                if (response in 300..399) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw IllegalStateException("媒体镜像重定向次数过多")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("媒体镜像返回了无效重定向")
                    current = RemoteMediaUrls.validate(current.resolve(location).toString())
                    return@repeat
                }
                if (response !in 200..299) {
                    throw HttpStatusException(response)
                }
                val declaredLength = connection.contentLengthLong
                if (declaredLength > maxBytes) {
                    throw IllegalStateException("媒体文件超过安全大小限制")
                }
                destination.parentFile?.let {
                    require(it.exists() || it.mkdirs()) { "无法创建媒体暂存目录" }
                }
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(destination).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maxBytes) {
                                throw IllegalStateException("媒体文件超过安全大小限制")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                        if (total <= 0L) throw IllegalStateException("媒体镜像返回了空文件")
                    }
                }
                return
            } finally {
                cancellation?.dispose()
                connection.disconnect()
            }
        }
    }

    private fun validateImage(file: File) {
        val type = imageType(file)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "下载的图片无法解码" }
        require(options.outMimeType.equals(type.mimeType, ignoreCase = true)) {
            "下载的图片类型与内容不一致"
        }
        require(options.outWidth <= MAX_IMAGE_DIMENSION && options.outHeight <= MAX_IMAGE_DIMENSION) {
            "图片尺寸超过安全限制"
        }
        var sampleSize = 1
        while (
            (options.outWidth / sampleSize).toLong() * (options.outHeight / sampleSize) >
            MAX_VALIDATION_PIXELS
        ) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw IllegalStateException("下载的图片像素数据不完整")
        decoded.recycle()
    }

    private fun imageType(file: File): ImageMediaType {
        val header = file.inputStream().use { input -> ByteArray(12).let { bytes -> bytes.copyOf(input.read(bytes).coerceAtLeast(0)) } }
        return ImageMediaType.fromHeader(header) ?: throw IllegalArgumentException("图片不是支持的 JPEG、PNG 或 WebP 格式")
    }

    private fun validateAudio(file: File) {
        require(file.length() >= MIN_AUDIO_BYTES) { "下载的音频文件不完整" }
        val header = ByteArray(3)
        file.inputStream().use { input -> input.read(header) }
        val hasId3 = header.contentEquals(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
        val hasMpegSync = header.size >= 2 &&
            (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0
        require(hasId3 || hasMpegSync) { "下载的音频不是有效的 MP3" }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            require(duration != null && duration > 0L) { "下载的音频无法完整读取" }
        } finally {
            retriever.release()
        }
    }

    private fun prepareEmptyDirectory(directory: File) {
        if (directory.exists()) directory.deleteRecursively()
        require(directory.mkdirs()) { "无法创建图文暂存目录" }
    }

    class HttpStatusException(val statusCode: Int) : Exception("HTTP Error $statusCode: 媒体服务器拒绝请求")

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 20_000
        private const val READ_TIMEOUT_MILLIS = 20_000
        private const val MAX_REDIRECTS = 5
        private const val MAX_IMAGE_BYTES = 50L * 1024L * 1024L
        private const val MAX_AUDIO_BYTES = 200L * 1024L * 1024L
        private const val MIN_AUDIO_BYTES = 1_024L
        private const val MAX_IMAGE_DIMENSION = 12_000
        private const val MAX_VALIDATION_PIXELS = 4_000_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36"
    }
}
