package com.flowframe.app.core.gallery

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URI
import javax.net.ssl.HttpsURLConnection

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
                val output = File(directory, "%03d.webp".format(index + 1))
                downloadFromMirrors(source.mirrors, output, MAX_IMAGE_BYTES)
                validateImage(output)
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
            downloadFromMirrors(audio.mirrors, output, MAX_AUDIO_BYTES)
            validateAudio(output)
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
    ) {
        var lastFailure: Throwable? = null
        for (mirror in mirrors.distinct()) {
            currentCoroutineContext().ensureActive()
            val partial = File(destination.parentFile, "${destination.name}.part")
            partial.delete()
            try {
                downloadOne(mirror, partial, maxBytes)
                if (!partial.renameTo(destination)) {
                    partial.copyTo(destination, overwrite = true)
                    partial.delete()
                }
                return
            } catch (error: Throwable) {
                partial.delete()
                currentCoroutineContext().ensureActive()
                lastFailure = error
            }
        }
        throw IllegalStateException("所有媒体镜像都暂时不可用", lastFailure)
    }

    private suspend fun downloadOne(rawUrl: String, destination: File, maxBytes: Long) {
        var current = validateRemoteUri(rawUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            currentCoroutineContext().ensureActive()
            val connection = (current.toURL().openConnection() as? HttpsURLConnection)
                ?: throw IllegalArgumentException("媒体地址不是安全的 HTTPS 地址")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Referer", "https://www.douyin.com/")
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/*,audio/mpeg,*/*;q=0.8")
            try {
                val response = connection.responseCode
                if (response in 300..399) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw IllegalStateException("媒体镜像重定向次数过多")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("媒体镜像返回了无效重定向")
                    current = validateRemoteUri(current.resolve(location).toString())
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
                connection.disconnect()
            }
        }
    }

    private fun validateRemoteUri(rawUrl: String): URI {
        val uri = runCatching { URI(rawUrl) }
            .getOrElse { throw IllegalArgumentException("媒体地址格式无效") }
        require(uri.scheme.equals("https", ignoreCase = true)) { "媒体地址必须使用 HTTPS" }
        require(uri.rawUserInfo == null) { "媒体地址不能包含账号信息" }
        require(uri.port == -1 || uri.port == 443) { "媒体地址不能使用非标准端口" }
        val host = uri.host?.trimEnd('.')?.lowercase()
            ?: throw IllegalArgumentException("媒体地址缺少域名")
        require(host.contains('.') && host != "localhost" && !host.endsWith(".local")) {
            "媒体地址域名无效"
        }
        require(!host.matches(IPV4_PATTERN) && !host.startsWith("[") && !host.endsWith("]")) {
            "媒体地址不能直接使用 IP"
        }
        // Signed asset hosts may change between mirrors. Resolve them, but never allow
        // loopback, site-local or link-local destinations.
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }
            .getOrElse { throw IllegalStateException("媒体域名暂时无法解析") }
        require(addresses.isNotEmpty() && addresses.none {
            it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                it.isSiteLocalAddress || it.isMulticastAddress
        }) { "媒体地址指向了不安全的网络位置" }
        return uri
    }

    private fun validateImage(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "下载的图片无法解码" }
        require(options.outMimeType.equals("image/webp", ignoreCase = true)) {
            "下载的图片不是预期的 WebP 格式"
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

    class HttpStatusException(val statusCode: Int) : Exception("媒体服务器返回 $statusCode")

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
        private val IPV4_PATTERN = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
    }
}
