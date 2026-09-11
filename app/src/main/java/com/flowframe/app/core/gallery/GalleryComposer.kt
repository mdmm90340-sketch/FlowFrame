package com.flowframe.app.core.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min

class GalleryComposer(private val context: Context) {
    suspend fun compose(
        gallery: GalleryAssetSet,
        assets: DownloadedGalleryAssets,
        output: File,
        onProgress: suspend (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        require(assets.images.isNotEmpty()) { "没有可合成的图片" }
        output.parentFile?.let { require(it.exists() || it.mkdirs()) { "无法创建合成目录" } }
        val frameDirectory = File(output.parentFile, "frames")
        if (frameDirectory.exists()) frameDirectory.deleteRecursively()
        require(frameDirectory.mkdirs()) { "无法创建合成帧目录" }
        val logFile = File(output.parentFile, "ffmpeg-compose.log")
        var process: Process? = null
        var logPump: Thread? = null
        try {
            val frames = assets.images.mapIndexed { index, image ->
                currentCoroutineContext().ensureActive()
                val frame = File(frameDirectory, "%03d.jpg".format(index + 1))
                renderFrame(image, frame)
                onProgress(((index + 1).toFloat() / assets.images.size) * 0.2f)
                frame
            }
            val durationMillis = GalleryTimeline.outputDurationMillis(
                audioDurationMillis = gallery.audio?.durationMillis,
                hasDownloadedAudio = assets.audio != null,
                imageCount = frames.size,
            )
            val boundaries = GalleryTimeline.transitionBoundariesMillis(
                beats = gallery.beatTimesMillis,
                durationMillis = durationMillis,
                imageCount = frames.size,
            )
            val command = buildCommand(frames, assets.audio, output, durationMillis, boundaries)
            val startedProcess = ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply {
                    val nativeDir = context.applicationInfo.nativeLibraryDir
                    val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
                    environment()["LD_LIBRARY_PATH"] = listOf(
                        nativeDir,
                        File(packages, "python/usr/lib").absolutePath,
                        File(packages, "ffmpeg/usr/lib").absolutePath,
                    ).joinToString(File.pathSeparator)
                }
                .start()
            process = startedProcess
            val encodedMicros = AtomicLong(0L)
            logPump = Thread({
                runCatching {
                    startedProcess.inputStream.bufferedReader(Charsets.UTF_8).use { input ->
                        logFile.bufferedWriter(Charsets.UTF_8).use { log ->
                            input.forEachLine { line ->
                                GalleryEncodingProgress.timeMicros(line)?.let { encodedMicros.set(max(encodedMicros.get(), it)) }
                                log.appendLine(line)
                            }
                        }
                    }
                }
            }, "FlowFrame-ffmpeg-log").apply {
                isDaemon = true
                start()
            }

            while (!process.hasExitedCompat()) {
                currentCoroutineContext().ensureActive()
                val fraction = GalleryEncodingProgress.fraction(encodedMicros.get(), durationMillis)
                onProgress(0.2f + fraction * 0.75f)
                delay(PROCESS_POLL_MILLIS)
            }
            if (process.exitValue() != 0) {
                throw IllegalStateException("图文视频合成失败（编码器返回 ${process.exitValue()}）")
            }
            validateOutput(output, durationMillis)
            onProgress(1f)
            output
        } catch (canceled: CancellationException) {
            process?.terminateNow()
            output.delete()
            throw canceled
        } catch (error: Throwable) {
            process?.terminateNow()
            output.delete()
            throw error
        } finally {
            process?.terminateNow()
            runCatching { process?.inputStream?.close() }
            runCatching { logPump?.join(LOG_PUMP_JOIN_MILLIS) }
            frameDirectory.deleteRecursively()
            logFile.delete()
        }
    }

    private fun renderFrame(source: File, destination: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片尺寸无效" }
        var sample = 1
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 4_000_000L) sample *= 2
        val original = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("图片无法解码，不能合成视频")
        require(original.width > 0 && original.height > 0) { "图片尺寸无效" }
        val output = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        try {
            canvas.drawColor(Color.BLACK)
            val crop = centerCropRect(original.width, original.height, OUTPUT_WIDTH, OUTPUT_HEIGHT)
            val softened = Bitmap.createBitmap(SOFT_BACKGROUND_WIDTH, SOFT_BACKGROUND_HEIGHT, Bitmap.Config.ARGB_8888)
            try {
                Canvas(softened).drawBitmap(
                    original,
                    crop,
                    Rect(0, 0, softened.width, softened.height),
                    paint,
                )
                canvas.drawBitmap(
                    softened,
                    Rect(0, 0, softened.width, softened.height),
                    Rect(0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT),
                    paint,
                )
            } finally {
                softened.recycle()
            }
            canvas.drawColor(Color.argb(94, 0, 0, 0))
            canvas.drawBitmap(
                original,
                Rect(0, 0, original.width, original.height),
                fitCenterRect(original.width, original.height, OUTPUT_WIDTH, OUTPUT_HEIGHT),
                paint,
            )
            FileOutputStream(destination).use { stream ->
                require(output.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "无法写入合成帧" }
            }
        } finally {
            original.recycle()
            output.recycle()
        }
    }

    private fun buildCommand(
        frames: List<File>,
        audio: File?,
        output: File,
        durationMillis: Long,
        boundariesMillis: List<Long>,
    ): List<String> = buildList {
        add(File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath)
        add("-hide_banner")
        add("-loglevel")
        add("warning")
        add("-progress")
        add("pipe:1")
        add("-nostats")
        add("-y")
        frames.forEach { frame ->
            add("-loop")
            add("1")
            add("-framerate")
            add("30")
            add("-i")
            add(frame.absolutePath)
        }
        if (audio != null) {
            add("-i")
            add(audio.absolutePath)
        }
        add("-filter_complex")
        add(buildFilter(frames.size, boundariesMillis))
        add("-map")
        add("[video]")
        if (audio != null) {
            add("-map")
            add("${frames.size}:a:0")
        } else {
            add("-an")
        }
        add("-t")
        add("%.3f".format(java.util.Locale.US, durationMillis / 1_000.0))
        add("-r")
        add("30")
        add("-c:v")
        add("libx264")
        add("-preset")
        add("veryfast")
        add("-crf")
        add("20")
        add("-pix_fmt")
        add("yuv420p")
        if (audio != null) {
            add("-c:a")
            add("aac")
            add("-b:a")
            add("192k")
        }
        add("-movflags")
        add("+faststart")
        add(output.absolutePath)
    }

    private fun buildFilter(imageCount: Int, boundariesMillis: List<Long>): String {
        val filters = mutableListOf<String>()
        repeat(imageCount) { index ->
            filters += "[$index:v]scale=iw:ih:in_range=pc:out_range=tv," +
                "settb=AVTB,setpts=PTS-STARTPTS,fps=30,format=yuv420p[v$index]"
        }
        if (imageCount == 1) {
            filters += "[v0]null[video]"
        } else {
            var previous = "v0"
            boundariesMillis.forEachIndexed { index, boundary ->
                val output = if (index == boundariesMillis.lastIndex) "video" else "x${index + 1}"
                val offset = max(0.0, boundary / 1_000.0 - TRANSITION_SECONDS / 2.0)
                filters += "[$previous][v${index + 1}]xfade=transition=fade:duration=$TRANSITION_SECONDS:" +
                    "offset=${"%.3f".format(java.util.Locale.US, offset)}[$output]"
                previous = output
            }
        }
        return filters.joinToString(";")
    }

    private fun centerCropRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val srcRatio = srcW.toDouble() / srcH
        val dstRatio = dstW.toDouble() / dstH
        return if (srcRatio > dstRatio) {
            val width = (srcH * dstRatio).toInt().coerceAtLeast(1)
            val left = (srcW - width) / 2
            Rect(left, 0, left + width, srcH)
        } else {
            val height = (srcW / dstRatio).toInt().coerceAtLeast(1)
            val top = (srcH - height) / 2
            Rect(0, top, srcW, top + height)
        }
    }

    private fun fitCenterRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val scale = min(dstW.toDouble() / srcW, dstH.toDouble() / srcH)
        val width = (srcW * scale).toInt().coerceAtLeast(1)
        val height = (srcH * scale).toInt().coerceAtLeast(1)
        val left = (dstW - width) / 2
        val top = (dstH - height) / 2
        return Rect(left, top, left + width, top + height)
    }

    private fun validateOutput(output: File, expectedDurationMillis: Long) {
        require(output.isFile && output.length() >= MIN_VIDEO_BYTES) { "合成视频文件不完整" }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            require(width == OUTPUT_WIDTH && height == OUTPUT_HEIGHT) { "合成视频尺寸不正确" }
            require(duration != null && kotlin.math.abs(duration - expectedDurationMillis) <= DURATION_TOLERANCE_MILLIS) {
                "合成视频时长不完整"
            }
        } finally {
            retriever.release()
        }
    }

    private fun Process.terminateNow() {
        if (hasExitedCompat()) return
        destroy()
        repeat(TERMINATION_POLL_COUNT) {
            if (hasExitedCompat()) return
            Thread.sleep(TERMINATION_POLL_MILLIS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            destroyForcibly()
        } else {
            destroy()
        }
    }

    private fun Process.hasExitedCompat(): Boolean = try {
        exitValue()
        true
    } catch (_: IllegalThreadStateException) {
        false
    }

    companion object {
        const val OUTPUT_WIDTH = 1080
        const val OUTPUT_HEIGHT = 1920
        private const val SOFT_BACKGROUND_WIDTH = 54
        private const val SOFT_BACKGROUND_HEIGHT = 96
        private const val TRANSITION_SECONDS = 0.25
        private const val PROCESS_POLL_MILLIS = 250L
        private const val LOG_PUMP_JOIN_MILLIS = 1_000L
        private const val TERMINATION_POLL_COUNT = 10
        private const val TERMINATION_POLL_MILLIS = 50L
        private const val MIN_VIDEO_BYTES = 16_384L
        private const val DURATION_TOLERANCE_MILLIS = 1_500L
    }
}
