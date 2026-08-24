package com.flowframe.app.core.engine

import android.content.Context
import com.flowframe.app.core.gallery.GalleryAssetSet
import com.flowframe.app.core.gallery.GalleryAudioSource
import com.flowframe.app.core.gallery.GalleryImageSource
import com.flowframe.app.core.model.MediaKind
import com.flowframe.app.core.model.MediaPreview
import com.flowframe.app.core.model.Platform
import com.flowframe.app.core.model.QualityPreset
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

class DownloadEngine(
    private val appContext: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return
        BundledYtDlpInstaller.prepareForLibraryInit(appContext)
        YoutubeDL.init(appContext)
        BundledYtDlpInstaller.verifyAndMark(appContext)
        FFmpeg.init(appContext)
        initialized = true
    }

    fun parse(url: String, platform: Platform, processId: String = "flowframe-parse") : MediaPreview {
        check(initialized) { "解析组件尚未就绪" }
        val raw = dumpInfoJson(url, processId)
        if (platform == Platform.DOUYIN) {
            val gallery = raw.galleryOrNull(url)
            if (gallery != null) {
                val firstImage = gallery.images.first()
                return MediaPreview(
                    sourceUrl = url,
                    platform = platform,
                    mediaId = gallery.mediaId,
                    title = gallery.title,
                    uploader = gallery.uploader,
                    // Signed CDN URLs are deliberately kept only inside GalleryAssetSet,
                    // which lives in memory for a single operation and is never persisted.
                    thumbnailUrl = null,
                    durationSeconds = ((gallery.audio?.durationMillis ?: 0L) / 1_000L).toInt(),
                    width = firstImage.width.coerceAtLeast(0),
                    height = firstImage.height.coerceAtLeast(0),
                    mediaKind = MediaKind.GALLERY,
                    imageCount = gallery.images.size,
                    hasAudio = gallery.audio != null,
                )
            }
        }
        return raw.toMediaPreview(url, platform)
    }

    fun resolveGallery(url: String, platform: Platform, processId: String): GalleryAssetSet {
        check(platform == Platform.DOUYIN) { "当前只有抖音图文需要独立解析" }
        check(initialized) { "解析组件尚未就绪" }
        return dumpInfoJson(url, processId).galleryOrNull(url)
            ?: throw IllegalStateException("这个链接不是可下载的图文作品")
    }

    fun buildDownloadRequest(
        url: String,
        preset: QualityPreset,
        outputDirectory: File,
        outputPrefix: String,
    ): YoutubeDLRequest {
        require(outputDirectory.exists() || outputDirectory.mkdirs()) {
            "无法创建下载目录"
        }
        check(initialized) { "解析组件尚未就绪" }

        return YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--newline")
            addOption("--no-mtime")
            addOption("--socket-timeout", "20")
            addOption("--retries", "2")
            addOption("--fragment-retries", "2")
            addOption("--concurrent-fragments", "3")
            addOption("--trim-filenames", "180")
            addOption("-P", outputDirectory.absolutePath)
            addOption("-o", "${outputPrefix}_%(title).160B [%(id)s].%(ext)s")

            when (preset) {
                QualityPreset.RECOMMENDED -> {
                    addOption("-f", "bv*+ba/b")
                    addOption("-S", "res:1080,vcodec:h264,acodec:aac")
                    addOption("--merge-output-format", "mp4")
                }

                QualityPreset.BEST -> {
                    addOption("-f", "bv*+ba/b")
                    addOption("--merge-output-format", "mp4/mkv")
                }

                QualityPreset.DATA_SAVER -> {
                    addOption("-f", "bv*+ba/b")
                    addOption("-S", "res:720,vcodec:h264,acodec:aac")
                    addOption("--merge-output-format", "mp4")
                }

                QualityPreset.AUDIO_ONLY -> {
                    addOption("-f", "ba/b")
                    addOption("--extract-audio")
                    addOption("--audio-format", "m4a")
                    addOption("--audio-quality", "0")
                }
            }
        }
    }

    fun execute(
        request: YoutubeDLRequest,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit,
    ) = YoutubeDL.execute(request, processId, onProgress)

    fun cancel(processId: String) {
        runCatching { YoutubeDL.destroyProcessById(processId) }
    }

    private fun dumpInfoJson(url: String, processId: String): JsonObject {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--skip-download")
            addOption("--dump-single-json")
            addOption("--no-warnings")
        }
        val response = YoutubeDL.execute(request, processId) { _, _, _ -> }
        val output = response.out.trim()
        val firstBrace = output.indexOf('{')
        val lastBrace = output.lastIndexOf('}')
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            throw IllegalStateException("解析器没有返回有效的媒体信息")
        }
        return runCatching {
            json.parseToJsonElement(output.substring(firstBrace, lastBrace + 1)).jsonObject
        }.getOrElse {
            throw IllegalStateException("解析器返回的媒体信息格式无效")
        }
    }

    private fun JsonObject.toMediaPreview(url: String, platform: Platform): MediaPreview {
        fun string(name: String) = this[name]?.jsonPrimitive?.contentOrNull
        fun int(name: String) = this[name]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0
        fun long(name: String) = this[name]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0L
        val mediaId = string("id").orEmpty().ifBlank { url.hashCode().toUInt().toString(16) }
        return MediaPreview(
            sourceUrl = url,
            platform = platform,
            mediaId = mediaId,
            title = string("title").orEmpty().ifBlank { "未命名视频" },
            uploader = string("uploader")?.takeIf(String::isNotBlank),
            thumbnailUrl = string("thumbnail")?.takeIf(String::isNotBlank),
            durationSeconds = int("duration").coerceAtLeast(0),
            width = int("width").coerceAtLeast(0),
            height = int("height").coerceAtLeast(0),
            estimatedSizeBytes = maxOf(long("filesize"), long("filesize_approx"), 0L),
            mediaKind = MediaKind.VIDEO,
        )
    }

    private fun JsonObject.galleryOrNull(sourceUrl: String): GalleryAssetSet? {
        val payload = this["flowframe_gallery"] as? JsonObject ?: return null
        val rawImages = payload["images"] as? JsonArray ?: return null
        val images = rawImages.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val mirrors = item.stringList("urls")
            if (mirrors.isEmpty()) return@mapNotNull null
            GalleryImageSource(
                width = item["width"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0,
                height = item["height"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0,
                mirrors = mirrors,
            )
        }
        if (images.isEmpty()) return null
        val audio = (payload["audio"] as? JsonObject)?.let { item ->
            item.stringList("urls").takeIf(List<String>::isNotEmpty)?.let { mirrors ->
                GalleryAudioSource(
                    mirrors = mirrors,
                    durationMillis = item["duration_ms"]?.jsonPrimitive?.longOrNull?.coerceAtLeast(0L) ?: 0L,
                    title = item["title"]?.jsonPrimitive?.contentOrNull,
                    artist = item["artist"]?.jsonPrimitive?.contentOrNull,
                )
            }
        }
        val beats = (payload["beats_ms"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.longOrNull }
            ?.filter { it >= 0L }
            ?.distinct()
            ?.sorted()
            .orEmpty()
        val id = this["id"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: sourceUrl.hashCode().toUInt().toString(16)
        return GalleryAssetSet(
            sourceUrl = sourceUrl,
            mediaId = id,
            title = this["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                ?: "未命名图文",
            uploader = this["uploader"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
            images = images,
            audio = audio,
            beatTimesMillis = beats,
        )
    }

    private fun JsonObject.stringList(name: String): List<String> =
        (this[name] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }
            ?.distinct()
            .orEmpty()
}
