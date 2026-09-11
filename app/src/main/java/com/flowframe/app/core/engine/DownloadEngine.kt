package com.flowframe.app.core.engine

import android.content.Context
import com.flowframe.app.core.gallery.GalleryAssetSet
import com.flowframe.app.core.gallery.GalleryAudioSource
import com.flowframe.app.core.gallery.GalleryImageSource
import com.flowframe.app.core.model.MediaKind
import com.flowframe.app.core.model.MediaFormat
import com.flowframe.app.core.model.MediaPreview
import com.flowframe.app.core.model.Platform
import com.flowframe.app.core.model.QualityPreset
import com.flowframe.app.core.platform.PlatformCatalog
import com.flowframe.app.core.platform.PublicContentException
import com.flowframe.app.core.platform.PublicPlatformResolver
import com.flowframe.app.core.platform.PublicPostParser
import com.flowframe.app.core.url.SupportedUrlParser
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
    private val publicResolver = PublicPlatformResolver()

    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return
        BundledYtDlpInstaller.prepareForLibraryInit(appContext)
        BundledNativeInstaller.prepare(appContext)
        YoutubeDL.init(appContext)
        BundledYtDlpInstaller.verifyAndMark(appContext)
        FFmpeg.init(appContext)
        BundledNativeInstaller.verify(appContext)
        initialized = true
    }

    fun parse(url: String, platform: Platform, processId: String = "flowframe-parse") : MediaPreview {
        check(initialized) { "解析组件尚未就绪" }
        require(SupportedUrlParser.validate(url)?.platform == platform) { "不支持的链接" }
        val native = if (platform in NATIVE_PLATFORMS) publicResolver.resolve(url, platform, processId) else null
        native?.gallery?.let { return it.toPreview(url, platform) }
        val mediaUrl = native?.videoUrl ?: native?.canonicalUrl ?: url
        if (native?.videoUrl != null) com.flowframe.app.core.gallery.RemoteMediaUrls.validate(mediaUrl)
        val raw = dumpInfoJson(mediaUrl, processId, if (native?.videoUrl != null) PlatformCatalog.get(platform).homeUrl else null)
        if (platform == Platform.DOUYIN) {
            val gallery = raw.galleryOrNull(url)
            if (gallery != null) {
                return gallery.toPreview(url, platform)
            }
        }
        val preview = MediaInfoSelection.singleVideo(raw, nativeConfirmedSingleVideo = native != null)
            .toMediaPreview(url, platform)
        return if (native != null) preview.copy(
            mediaId = native.mediaId,
            title = native.title,
            uploader = native.uploader ?: preview.uploader,
            thumbnailUrl = native.thumbnailUrl ?: preview.thumbnailUrl,
        ) else preview
    }

    fun resolveGallery(url: String, platform: Platform, processId: String): GalleryAssetSet {
        check(initialized) { "解析组件尚未就绪" }
        if (platform in NATIVE_PLATFORMS) return publicResolver.resolve(url, platform, processId).gallery
            ?: throw IllegalStateException("这个链接不是可下载的图文作品")
        check(platform == Platform.DOUYIN) { "这个平台没有图文下载能力" }
        return dumpInfoJson(url, processId).galleryOrNull(url)
            ?: throw IllegalStateException("这个链接不是可下载的图文作品")
    }

    fun buildDownloadRequest(
        url: String,
        preset: QualityPreset,
        outputDirectory: File,
        outputPrefix: String,
        formatId: String? = null,
        platform: Platform? = null,
        processId: String = "flowframe-download-resolve",
    ): YoutubeDLRequest {
        require(outputDirectory.exists() || outputDirectory.mkdirs()) {
            "无法创建下载目录"
        }
        check(initialized) { "解析组件尚未就绪" }
        val supported = SupportedUrlParser.validate(url) ?: throw IllegalArgumentException("不支持的链接")
        require(platform == null || platform == supported.platform) { "不支持的链接" }
        val native = if (supported.platform in NATIVE_PLATFORMS) publicResolver.resolve(url, supported.platform, processId) else null
        if (native?.gallery != null) throw IllegalStateException("这个作品是图文，请重新解析并选择图文保存方式")
        val downloadUrl = native?.videoUrl ?: native?.canonicalUrl ?: url
        if (native?.videoUrl != null) com.flowframe.app.core.gallery.RemoteMediaUrls.validate(downloadUrl)

        return YoutubeDLRequest(downloadUrl).apply {
            if (native?.videoUrl != null) addOption("--referer", PlatformCatalog.get(supported.platform).homeUrl)
            addOption("--no-playlist")
            addOption("--newline")
            addOption("--progress-template", "download:FLOWFRAME_PROGRESS:%(progress.downloaded_bytes)j|%(progress.total_bytes)j|%(progress.total_bytes_estimate)j|%(progress.speed)j|%(progress.eta)j")
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
            if (!formatId.isNullOrBlank()) {
                require(FORMAT_ID.matches(formatId)) { "没有可用格式：格式编号无效" }
                addOption("-f", formatId)
            }
        }
    }

    fun execute(
        request: YoutubeDLRequest,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit,
    ) = YoutubeDL.execute(request, processId, onProgress)

    fun cancel(processId: String) {
        publicResolver.cancel(processId)
        runCatching { YoutubeDL.destroyProcessById(processId) }
    }

    private fun dumpInfoJson(url: String, processId: String, referer: String? = null): JsonObject {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--skip-download")
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            addOption("--retries", "1")
            if (referer != null) addOption("--referer", referer)
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
        if (this["entries"] is JsonArray) throw PublicContentException(PublicPostParser.MIXED_CONTENT_MESSAGE)
        fun string(name: String) = this[name]?.jsonPrimitive?.contentOrNull
        fun int(name: String) = this[name]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0
        fun long(name: String) = this[name]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0L
        val mediaId = string("id").orEmpty().ifBlank { url.hashCode().toUInt().toString(16) }
        val formats = (this["formats"] as? JsonArray).orEmpty().mapNotNull { element ->
            val format = element as? JsonObject ?: return@mapNotNull null
            val id = format["format_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (format["url"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) return@mapNotNull null
            MediaFormat(
                id = id,
                ext = format["ext"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                width = format["width"]?.jsonPrimitive?.intOrNull ?: 0,
                height = format["height"]?.jsonPrimitive?.intOrNull ?: 0,
                videoCodec = format["vcodec"]?.jsonPrimitive?.contentOrNull,
                audioCodec = format["acodec"]?.jsonPrimitive?.contentOrNull,
                filesizeBytes = format["filesize"]?.jsonPrimitive?.longOrNull ?: format["filesize_approx"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }.distinctBy { it.id }
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
            hasAudio = string("acodec")?.let { it != "none" } == true || formats.any { !it.audioCodec.isNullOrBlank() && it.audioCodec != "none" },
            formats = formats,
        )
    }

    private fun GalleryAssetSet.toPreview(url: String, platform: Platform): MediaPreview {
        val first = images.first()
        return MediaPreview(
            sourceUrl = url, platform = platform, mediaId = mediaId, title = title, uploader = uploader,
            thumbnailUrl = first.mirrors.first(),
            durationSeconds = ((audio?.durationMillis ?: 0L) / 1_000L).toInt(),
            width = first.width, height = first.height, mediaKind = MediaKind.GALLERY,
            imageCount = images.size, hasAudio = audio != null,
            imageUrls = images.map { it.mirrors.first() },
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
        if (images.size != rawImages.size) {
            throw PublicContentException("公开页面未提供可下载媒体：图集图片信息不完整，请重新复制作品分享链接")
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

    companion object {
        private val NATIVE_PLATFORMS = setOf(Platform.XIAOHONGSHU, Platform.WEIBO, Platform.KUAISHOU)
        private val FORMAT_ID = Regex("[A-Za-z0-9_.+-]{1,128}")
    }
}
