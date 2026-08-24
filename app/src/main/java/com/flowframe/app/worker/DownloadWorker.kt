package com.flowframe.app.worker

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.flowframe.app.FlowFrameApplication
import com.flowframe.app.core.error.FailureClassifier
import com.flowframe.app.core.error.FailureOperation
import com.flowframe.app.core.gallery.DownloadedGalleryAssets
import com.flowframe.app.core.gallery.GalleryAssetDownloader
import com.flowframe.app.core.gallery.GalleryAssetSet
import com.flowframe.app.core.gallery.GalleryComposer
import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.GalleryOutputMode
import com.flowframe.app.core.model.MediaKind
import com.flowframe.app.core.model.QualityPreset
import com.flowframe.app.core.model.TaskStage
import com.flowframe.app.storage.MediaPublisher
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val app = appContext as FlowFrameApplication
    private val store = app.container.taskStore
    private val engine = app.container.engine
    private val processId = id.toString()
    private val galleryDownloader = GalleryAssetDownloader()
    private val galleryComposer = GalleryComposer(appContext)

    override suspend fun doWork() = coroutineScope {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@coroutineScope Result.failure()
        var task = store.find(taskId) ?: return@coroutineScope Result.failure()
        setForeground(DownloadNotifications.foregroundInfo(applicationContext, task))
        var hasConcurrencyPermit = false
        var completed = false
        var retainedPaths = emptySet<String>()
        var publishedLocations = emptyList<String>()
        val taskDirectory = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "FlowFrame/${task.id}",
        )

        try {
            app.container.downloadGate.acquire()
            hasConcurrencyPermit = true
            app.container.awaitEngine()
            ensureActive()
            store.update(taskId) {
                it.copy(
                    stage = TaskStage.RESOLVING,
                    progress = 0f,
                    etaSeconds = null,
                    outputLocation = null,
                    outputLocations = emptyList(),
                    outputSizeBytes = 0L,
                    errorMessage = null,
                )
            }
            task = store.find(taskId) ?: return@coroutineScope Result.failure()
            if (taskDirectory.exists()) taskDirectory.deleteRecursively()
            require(taskDirectory.mkdirs()) { "无法创建下载暂存目录" }

            val result = when (task.mediaKind) {
                MediaKind.VIDEO -> downloadVideo(task, taskDirectory)
                MediaKind.GALLERY -> downloadGalleryWithRefresh(task, taskDirectory)
            }
            publishedLocations = result.locations
            store.update(taskId) {
                it.copy(
                    stage = TaskStage.COMPLETED,
                    progress = 1f,
                    etaSeconds = null,
                    outputLocation = result.locations.firstOrNull(),
                    outputLocations = result.locations,
                    outputSizeBytes = result.sizeBytes,
                    errorMessage = null,
                )
            }
            retainedPaths = result.locations
                .filterNot { it.startsWith("content://") }
                .map { File(it).absolutePath }
                .toSet()
            completed = true
            store.find(taskId)?.let { DownloadNotifications.update(applicationContext, it) }
            Result.success(workDataOf(KEY_OUTPUT_LOCATION to result.locations.firstOrNull().orEmpty()))
        } catch (canceled: CancellationException) {
            store.update(taskId) { it.copy(stage = TaskStage.CANCELED, etaSeconds = null) }
            throw canceled
        } catch (canceled: YoutubeDL.CanceledException) {
            store.update(taskId) { it.copy(stage = TaskStage.CANCELED, etaSeconds = null) }
            Result.failure()
        } catch (interrupted: InterruptedException) {
            store.update(taskId) { it.copy(stage = TaskStage.CANCELED, etaSeconds = null) }
            Result.failure()
        } catch (error: YoutubeDLException) {
            handleFailure(taskId, error)
        } catch (error: Throwable) {
            handleFailure(taskId, error)
        } finally {
            engine.cancel(processId)
            if (!completed) rollbackPublished(publishedLocations)
            if (!completed || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                taskDirectory.deleteRecursively()
            } else {
                removeTemporaryFiles(taskDirectory, retainedPaths)
            }
            if (hasConcurrencyPermit) app.container.downloadGate.release()
        }
    }

    private suspend fun downloadVideo(task: DownloadTask, outputDirectory: File): PublishedResult {
        val prefix = task.id.take(8)
        val request = engine.buildDownloadRequest(
            url = task.sourceUrl,
            preset = task.preset,
            outputDirectory = outputDirectory,
            outputPrefix = prefix,
        )
        val events = Channel<ProgressEvent>(Channel.CONFLATED)
        val lastUpdate = AtomicLong(0L)
        coroutineScope {
            val collector = launch {
                for (event in events) updateProgress(task.id, event)
            }
            try {
                withContext(Dispatchers.IO) {
                    engine.execute(request, processId) { percent, eta, line ->
                        val now = SystemClock.elapsedRealtime()
                        val merging = line.contains("[Merger]") ||
                            line.contains("[ExtractAudio]") ||
                            line.contains("[VideoRemuxer]")
                        if (merging || now - lastUpdate.get() >= PROGRESS_THROTTLE_MS) {
                            lastUpdate.set(now)
                            events.trySend(
                                ProgressEvent(
                                    stage = if (merging) TaskStage.MERGING else TaskStage.DOWNLOADING,
                                    progress = if (merging) 0.98f else (percent / 100f).coerceIn(0f, 0.97f),
                                    etaSeconds = eta.takeIf { it >= 0 },
                                ),
                            )
                        }
                    }
                }
            } finally {
                events.close()
                collector.join()
            }
        }

        val output = outputDirectory.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile && file.name.startsWith("${prefix}_") &&
                    !file.name.endsWith(".part") && !file.name.endsWith(".ytdl")
            }
            .maxByOrNull(File::lastModified)
            ?: throw IllegalStateException("下载已结束，但没有找到输出文件")
        val size = output.length().coerceAtLeast(0L)
        val location = if (task.preset == QualityPreset.AUDIO_ONLY) {
            MediaPublisher.publishAudio(applicationContext, output)
        } else {
            MediaPublisher.publishVideo(applicationContext, output)
        }
        return PublishedResult(listOf(location), size)
    }

    private suspend fun downloadGalleryWithRefresh(
        task: DownloadTask,
        taskDirectory: File,
    ): PublishedResult {
        var gallery = resolveGallery(task)
        repeat(2) { attempt ->
            try {
                return performGalleryTask(task, gallery, taskDirectory)
            } catch (error: Throwable) {
                val expired = error.hasHttpStatus(403) || error.hasHttpStatus(404)
                if (!expired || attempt > 0) throw error
                taskDirectory.deleteRecursively()
                require(taskDirectory.mkdirs()) { "无法重建图文暂存目录" }
                updateProgress(task.id, ProgressEvent(TaskStage.RESOLVING, 0f, null))
                gallery = resolveGallery(task)
            }
        }
        throw IllegalStateException("图文下载失败")
    }

    private suspend fun resolveGallery(task: DownloadTask): GalleryAssetSet =
        withContext(Dispatchers.IO) {
            engine.resolveGallery(task.sourceUrl, task.platform, processId)
        }

    private suspend fun performGalleryTask(
        task: DownloadTask,
        gallery: GalleryAssetSet,
        taskDirectory: File,
    ): PublishedResult {
        ensureUsableSpace(taskDirectory, MIN_GALLERY_FREE_BYTES)
        val assetsDirectory = File(taskDirectory, "assets")
        return when (task.galleryOutputMode ?: GalleryOutputMode.MP4) {
            GalleryOutputMode.IMAGES -> {
                val images = galleryDownloader.downloadImages(gallery, assetsDirectory) { completed, total ->
                    updateProgress(
                        task.id,
                        ProgressEvent(TaskStage.DOWNLOADING, completed.toFloat() / total, null),
                    )
                }.mapIndexed { index, file ->
                    rename(file, File(file.parentFile, "%03d_%s.webp".format(index + 1, safeName(task.title))))
                }
                val size = images.sumOf(File::length)
                val locations = MediaPublisher.publishImages(
                    context = applicationContext,
                    sources = images,
                    folderName = "${task.title}-${task.mediaId}",
                )
                PublishedResult(locations, size)
            }

            GalleryOutputMode.AUDIO -> {
                val audioSource = gallery.audio
                    ?: throw IllegalStateException("这条图文作品没有背景音乐")
                updateProgress(task.id, ProgressEvent(TaskStage.DOWNLOADING, 0.05f, null))
                val downloaded = galleryDownloader.downloadAudio(audioSource, assetsDirectory)
                val audio = rename(
                    downloaded,
                    File(taskDirectory, "${task.id.take(8)}_${safeName(task.title)} [${task.mediaId}].mp3"),
                )
                updateProgress(task.id, ProgressEvent(TaskStage.DOWNLOADING, 0.95f, null))
                val size = audio.length()
                PublishedResult(listOf(MediaPublisher.publishAudio(applicationContext, audio)), size)
            }

            GalleryOutputMode.MP4 -> {
                val images = galleryDownloader.downloadImages(
                    gallery,
                    File(assetsDirectory, "images"),
                ) { completed, total ->
                    updateProgress(
                        task.id,
                        ProgressEvent(TaskStage.DOWNLOADING, completed.toFloat() / total * 0.65f, null),
                    )
                }
                val audio = gallery.audio?.let { source ->
                    galleryDownloader.downloadAudio(source, assetsDirectory)
                }
                val output = File(
                    taskDirectory,
                    "${task.id.take(8)}_${safeName(task.title)} [${task.mediaId}].mp4",
                )
                updateProgress(task.id, ProgressEvent(TaskStage.MERGING, 0.66f, null))
                galleryComposer.compose(
                    gallery = gallery,
                    assets = DownloadedGalleryAssets(images, audio),
                    output = output,
                ) { composeProgress ->
                    updateProgress(
                        task.id,
                        ProgressEvent(TaskStage.MERGING, 0.66f + composeProgress * 0.33f, null),
                    )
                }
                val size = output.length()
                PublishedResult(listOf(MediaPublisher.publishVideo(applicationContext, output)), size)
            }
        }
    }

    private suspend fun updateProgress(taskId: String, event: ProgressEvent) {
        store.update(taskId) { current ->
            current.copy(
                stage = event.stage,
                progress = maxOf(current.progress, event.progress.coerceIn(0f, 0.99f)),
                etaSeconds = event.etaSeconds,
            )
        }
        store.find(taskId)?.let { DownloadNotifications.update(applicationContext, it) }
        setProgress(
            workDataOf(
                KEY_PROGRESS to event.progress,
                KEY_ETA to (event.etaSeconds ?: -1L),
            ),
        )
    }

    private suspend fun handleFailure(taskId: String, error: Throwable): Result {
        val failure = FailureClassifier.classify(error, FailureOperation.DOWNLOAD)
        Log.w(LOG_TAG, "Download failed [${failure.kind}]: ${failure.diagnostic}")
        if (failure.retryable && runAttemptCount < 2) {
            store.update(taskId) { it.copy(stage = TaskStage.QUEUED, errorMessage = "网络波动，准备重试") }
            return Result.retry()
        }
        val message = failure.userMessage
        store.update(taskId) { it.copy(stage = TaskStage.FAILED, etaSeconds = null, errorMessage = message) }
        store.find(taskId)?.let { DownloadNotifications.update(applicationContext, it) }
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    private fun Throwable.hasHttpStatus(status: Int): Boolean {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = this
        while (current != null && seen.add(current)) {
            if (current is GalleryAssetDownloader.HttpStatusException && current.statusCode == status) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun ensureUsableSpace(directory: File, requiredBytes: Long) {
        val available = directory.usableSpace
        require(available <= 0L || available >= requiredBytes) { "No space left on device" }
    }

    private fun rollbackPublished(locations: List<String>) {
        locations.filter { it.startsWith("content://") }.forEach { location ->
            runCatching { applicationContext.contentResolver.delete(Uri.parse(location), null, null) }
        }
    }

    private fun rename(source: File, destination: File): File {
        destination.parentFile?.let { require(it.exists() || it.mkdirs()) { "无法创建输出目录" } }
        if (source.absolutePath == destination.absolutePath) return source
        if (!source.renameTo(destination)) {
            source.copyTo(destination, overwrite = true)
            require(source.delete()) { "无法清理媒体暂存文件" }
        }
        return destination
    }

    private fun safeName(value: String): String = value
        .replace(Regex("""[\\/:*?\"<>|\p{Cntrl}]"""), "_")
        .trim(' ', '.')
        .take(90)
        .ifBlank { "未命名作品" }

    private fun removeTemporaryFiles(directory: File, retainedPaths: Set<String>) {
        directory.walkBottomUp().forEach { file ->
            if (file.isFile && file.absolutePath !in retainedPaths) {
                file.delete()
            } else if (file.isDirectory && file.listFiles().isNullOrEmpty()) {
                file.delete()
            }
        }
    }

    private data class PublishedResult(
        val locations: List<String>,
        val sizeBytes: Long,
    )

    private data class ProgressEvent(
        val stage: TaskStage,
        val progress: Float,
        val etaSeconds: Long?,
    )

    companion object {
        private const val LOG_TAG = "FlowFrameDownload"
        const val KEY_TASK_ID = "task_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ETA = "eta_seconds"
        const val KEY_OUTPUT_LOCATION = "output_location"
        const val KEY_ERROR = "error"
        private const val PROGRESS_THROTTLE_MS = 400L
        private const val MIN_GALLERY_FREE_BYTES = 64L * 1024L * 1024L
    }
}
