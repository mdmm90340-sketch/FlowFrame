package com.flowframe.app.core.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowframe.app.FlowFrameApplication
import com.flowframe.app.storage.MediaPublisher
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the packaged native runtime and real storage using generated, non-personal media only. */
@RunWith(AndroidJUnit4::class)
class GalleryPipelineInstrumentedTest {
    @Test(timeout = 240_000)
    fun generatedImagesComposeDecodeAndPublishThroughProductionPipeline(): Unit = runBlocking<Unit> {
        withTimeout(220_000) {
            val app = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext as FlowFrameApplication
            val testDirectory = File(app.cacheDir, "flowframe-pipeline-test-" + UUID.randomUUID())
            require(testDirectory.mkdirs())
            val createdUris = mutableListOf<Uri>()
            try {
                app.container.awaitEngine()
                val png = generateImage(testDirectory, "frame-a.png", Bitmap.CompressFormat.PNG, 480, 640, Color.rgb(79, 76, 194))
                val jpeg = generateImage(testDirectory, "frame-b.jpg", Bitmap.CompressFormat.JPEG, 640, 360, Color.rgb(0, 134, 128))
                @Suppress("DEPRECATION")
                val webp = generateImage(testDirectory, "frame-c.webp", Bitmap.CompressFormat.WEBP, 640, 480, Color.rgb(173, 64, 119))
                val sources = listOf(png, jpeg, webp)
                val expectedBytes = sources.map(File::readBytes)
                val expectedMimes = listOf("image/png", "image/jpeg", "image/webp")
                sources.forEach { source ->
                    val bitmap = BitmapFactory.decodeFile(source.absolutePath)
                    assertNotNull("Generated fixture must decode", bitmap)
                    bitmap!!.recycle()
                }
                // Exercise the bundled WebP decoder too, not only Android's BitmapFactory.
                decodeEveryFrame(app, webp)

                val gallery = GalleryAssetSet(
                    sourceUrl = "https://example.invalid/generated-test",
                    mediaId = "generated-test",
                    title = "Generated test gallery",
                    uploader = null,
                    images = listOf(
                        GalleryImageSource(width = 480, height = 640, mirrors = emptyList()),
                        GalleryImageSource(width = 640, height = 480, mirrors = emptyList()),
                    ),
                    audio = null,
                    beatTimesMillis = emptyList(),
                )
                val output = File(testDirectory, "generated-gallery.mp4")
                val progress = mutableListOf<Float>()
                GalleryComposer(app).compose(
                    gallery,
                    DownloadedGalleryAssets(images = listOf(png, webp), audio = null),
                    output,
                ) { progress += it }
                assertTrue(output.isFile && output.length() > 16_384L)
                assertTrue(progress.isNotEmpty())
                assertEquals(1f, progress.last(), 0.001f)
                verifyVideoMetadata(output.absolutePath)
                decodeEveryFrame(app, output)

                if (Build.VERSION.SDK_INT >= 29) {
                    val videoBytes = output.readBytes()
                    val locations = MediaPublisher.publishImages(app, sources, testDirectory.name)
                    createdUris += locations.map(Uri::parse)
                    assertEquals(3, locations.size)
                    locations.forEachIndexed { index, location ->
                        val uri = Uri.parse(location)
                        assertEquals(expectedMimes[index], app.contentResolver.getType(uri))
                        val bytes = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                        assertArrayEquals(expectedBytes[index], bytes)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        assertNotNull("Published image must decode", bitmap)
                        bitmap!!.recycle()
                    }
                    val videoLocation = MediaPublisher.publishVideo(app, output)
                    val videoUri = Uri.parse(videoLocation)
                    createdUris += videoUri
                    assertEquals("video/mp4", app.contentResolver.getType(videoUri))
                    app.contentResolver.openInputStream(videoUri)!!.use {
                        assertArrayEquals(videoBytes, it.readBytes())
                    }
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(app, videoUri)
                        assertEquals("1080", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                        assertEquals("1920", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                        val frame = retriever.getFrameAtTime(4_500_000)
                        assertNotNull("Published MP4 must remain readable", frame)
                        frame?.recycle()
                    } finally {
                        retriever.release()
                    }
                } else {
                    // Legacy Android uses local app files; do not claim MediaStore coverage here.
                    assertEquals(sources.map(File::getAbsolutePath), MediaPublisher.publishImages(app, sources, testDirectory.name))
                    assertEquals(output.absolutePath, MediaPublisher.publishVideo(app, output))
                    sources.forEach { assertTrue(it.isFile && it.length() > 0) }
                }
            } finally {
                createdUris.forEach { uri ->
                    // Only URIs returned to this specific test are eligible for deletion.
                    app.contentResolver.delete(uri, null, null)
                }
                require(testDirectory.canonicalFile.parentFile == app.cacheDir.canonicalFile)
                require(testDirectory.name.startsWith("flowframe-pipeline-test-"))
                testDirectory.deleteRecursively()
            }
        }
    }

    private fun generateImage(
        directory: File,
        name: String,
        format: Bitmap.CompressFormat,
        width: Int,
        height: Int,
        color: Int,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(color)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }
            canvas.drawCircle(width * 0.5f, height * 0.45f, minOf(width, height) * 0.22f, paint)
            paint.color = Color.rgb(35, 38, 51)
            canvas.drawRect(width * 0.18f, height * 0.78f, width * 0.82f, height * 0.85f, paint)
            return File(directory, name).also { file ->
                file.outputStream().use { assertTrue(bitmap.compress(format, 92, it)) }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun verifyVideoMetadata(path: String) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            assertEquals("1080", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
            assertEquals("1920", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Two images must produce approximately six seconds", abs(duration - 6_000) <= 500)
            listOf(500_000L, 5_500_000L).forEach { time ->
                val frame = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)
                assertNotNull("Both ends of the MP4 must decode", frame)
                frame?.recycle()
            }
        } finally {
            retriever.release()
        }
    }

    private suspend fun decodeEveryFrame(app: FlowFrameApplication, source: File) = withContext(Dispatchers.IO) {
        val nativeDirectory = app.applicationInfo.nativeLibraryDir
        val packages = File(app.noBackupFilesDir, "youtubedl-android/packages")
        val command = listOf(
            File(nativeDirectory, "libffmpeg.so").absolutePath,
            "-v", "error", "-xerror", "-i", source.absolutePath,
            "-map", "0:v:0", "-f", "null", "-",
        )
        val process = ProcessBuilder(command).redirectErrorStream(true).apply {
            environment()["LD_LIBRARY_PATH"] = listOf(
                nativeDirectory,
                File(packages, "python/usr/lib").absolutePath,
                File(packages, "ffmpeg/usr/lib").absolutePath,
            ).joinToString(File.pathSeparator)
        }.start()
        val log = ByteArrayOutputStream()
        val pump = Thread({
            runCatching { process.inputStream.use { it.copyTo(log) } }
        }, "FlowFrame-test-decode-log").apply { isDaemon = true; start() }
        try {
            withTimeout(90_000) {
                while (!process.hasExited()) delay(100)
            }
            pump.join(1_000)
            assertEquals("Full FFmpeg decode failed: " + log.toString("UTF-8"), 0, process.exitValue())
        } finally {
            process.destroy()
            runCatching { process.inputStream.close() }
            pump.join(1_000)
        }
    }

    private fun Process.hasExited(): Boolean = try {
        exitValue()
        true
    } catch (_: IllegalThreadStateException) {
        false
    }
}
