package com.flowframe.app.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadTaskMigrationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun version101TaskKeepsLegacyOutputLocation() {
        val legacy = """
            {
              "id":"old-task",
              "sourceUrl":"https://b23.tv/DemoBV01",
              "platform":"BILIBILI",
              "mediaId":"BV1atXsBREMJ_p1",
              "title":"旧任务",
              "preset":"RECOMMENDED",
              "stage":"COMPLETED",
              "outputLocation":"content://media/external/video/media/1",
              "outputSizeBytes":1234
            }
        """.trimIndent()

        val task = json.decodeFromString<DownloadTask>(legacy)

        assertEquals(MediaKind.VIDEO, task.mediaKind)
        assertNull(task.galleryOutputMode)
        assertEquals(emptyList<String>(), task.outputLocations)
        assertEquals(listOf("content://media/external/video/media/1"), task.resolvedOutputLocations)
    }

    @Test
    fun galleryOutputsRoundTripWithoutUsingLegacyField() {
        val original = DownloadTask(
            id = "gallery-task",
            sourceUrl = "https://v.douyin.com/DemoGallery_02/",
            platform = Platform.DOUYIN,
            mediaId = "7677618782324396657",
            title = "示例图文任务",
            preset = QualityPreset.RECOMMENDED,
            mediaKind = MediaKind.GALLERY,
            galleryOutputMode = GalleryOutputMode.IMAGES,
            outputLocations = listOf("content://image/1", "content://image/2"),
        )

        val decoded = json.decodeFromString<DownloadTask>(json.encodeToString(DownloadTask.serializer(), original))

        assertEquals(GalleryOutputMode.IMAGES, decoded.galleryOutputMode)
        assertEquals(original.outputLocations, decoded.resolvedOutputLocations)
    }
}
