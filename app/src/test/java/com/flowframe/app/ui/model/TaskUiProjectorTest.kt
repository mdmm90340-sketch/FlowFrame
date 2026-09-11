package com.flowframe.app.ui.model

import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.GalleryOutputMode
import com.flowframe.app.core.model.MediaKind
import com.flowframe.app.core.model.Platform
import com.flowframe.app.core.model.QualityPreset
import com.flowframe.app.core.model.TaskStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TaskUiProjectorTest {
    @Test
    fun progressUpdatesReuseTheOther999HistoryRows() {
        val projector = TaskUiProjector()
        var tasks = List(1_000) { fixture("task-$it") }
        var rows = projector.project(tasks, ONLINE, emptyMap())
        var rebuilt = 0
        repeat(5) { step ->
            tasks = tasks.toMutableList().apply { this[0] = first().copy(progress = (step + 1) / 10f) }
            val next = projector.project(tasks, ONLINE, emptyMap())
            rows.indices.forEach { index -> if (rows[index] !== next[index]) rebuilt++ }
            assertEquals((step + 1) / 10f, next.first().progress)
            for (index in 1 until rows.size) assertSame(rows[index], next[index])
            rows = next
        }
        assertEquals(5, rebuilt)
    }

    @Test
    fun aBackendTimestampChangeDoesNotChangeThePresentedList() {
        val projector = TaskUiProjector()
        val task = fixture("task")
        val rows = projector.project(listOf(task), ONLINE, emptyMap())
        val next = projector.project(listOf(task.copy(updatedAtEpochMillis = 42L)), ONLINE, emptyMap())
        assertSame(rows, next)
    }

    @Test
    fun queuedReasonsFollowOfflineThenWifiThenWorkerReason() {
        val projector = TaskUiProjector()
        val tasks = listOf(fixture("queued").copy(stage = TaskStage.QUEUED, errorMessage = "等待槽位"), fixture("active"))
        val offline = projector.project(tasks, TaskNetworkConstraints(false, false, true), emptyMap())
        assertEquals("等待网络恢复", offline[0].errorMessage)
        assertNull(offline[0].progress)
        val mobile = projector.project(tasks, TaskNetworkConstraints(true, false, true), emptyMap())
        assertEquals("等待 Wi-Fi 网络", mobile[0].errorMessage)
        assertSame(offline[1], mobile[1])
        val wifi = projector.project(tasks, TaskNetworkConstraints(true, true, true), emptyMap())
        assertEquals("等待槽位", wifi[0].errorMessage)
        val unrestricted = projector.project(tasks, TaskNetworkConstraints(true, false, false), emptyMap())
        assertEquals("等待槽位", unrestricted[0].errorMessage)
        assertSame(wifi, unrestricted)
    }

    @Test
    fun previewThumbnailRefreshAndEvictionUpdateOnlyTheirOwnRow() {
        val projector = TaskUiProjector()
        val tasks = listOf(fixture("first"), fixture("second"))
        val first = projector.project(tasks, ONLINE, mapOf("DOUYIN:first" to "https://cdn.example/a.jpg"))
        assertEquals("https://cdn.example/a.jpg", first[0].thumbnailUrl)
        val refreshed = projector.project(tasks, ONLINE, mapOf("DOUYIN:first" to "https://cdn.example/b.jpg"))
        assertEquals("https://cdn.example/b.jpg", refreshed[0].thumbnailUrl)
        assertSame(first[1], refreshed[1])
        val evicted = projector.project(tasks, ONLINE, emptyMap())
        assertNull(evicted[0].thumbnailUrl)
        assertSame(first[1], evicted[1])
    }

    @Test
    fun completedAndCanceledRowsRetainOutputAndStatusSemantics() {
        val projector = TaskUiProjector()
        val tasks = listOf(
            fixture("completed").copy(
                stage = TaskStage.COMPLETED,
                mediaKind = MediaKind.GALLERY,
                galleryOutputMode = GalleryOutputMode.IMAGES,
                outputLocations = listOf("content://example/one", "content://example/two"),
                outputSizeBytes = 8_192L,
            ),
            fixture("legacy").copy(stage = TaskStage.COMPLETED, outputLocation = "content://example/legacy"),
            fixture("canceled").copy(stage = TaskStage.CANCELED, errorMessage = "Old worker error"),
        )
        val rows = projector.project(tasks, ONLINE, emptyMap())
        assertEquals(DownloadTaskStage.Completed, rows[0].stage)
        assertEquals("已保存 2 张", rows[0].completedGallerySummary)
        assertEquals(8_192L, rows[0].downloadedBytes)
        assertEquals(8_192L, rows[0].totalBytes)
        assertEquals(listOf("content://example/legacy"), rows[1].outputLocations)
        assertNull(rows[1].totalBytes)
        assertEquals(DownloadTaskStage.Canceled, rows[2].stage)
        assertEquals("任务已取消", rows[2].errorMessage)
    }

    @Test
    fun deletedTaskRowsLeaveTheProjectionCache() {
        val projector = TaskUiProjector()
        val task = fixture("task")
        val before = projector.project(listOf(task), ONLINE, emptyMap()).single()
        assertEquals(emptyList<DownloadTaskUi>(), projector.project(emptyList(), ONLINE, emptyMap()))
        val after = projector.project(listOf(task), ONLINE, emptyMap()).single()
        assertEquals(before, after)
        assertNotSame(before, after)
    }

    private fun fixture(id: String) = DownloadTask(
        id = id, sourceUrl = "https://example.invalid/generated", platform = Platform.DOUYIN,
        mediaId = id, title = "Generated task", preset = QualityPreset.RECOMMENDED,
        stage = TaskStage.DOWNLOADING, createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )

    private companion object {
        val ONLINE = TaskNetworkConstraints(connected = true, wifi = true, wifiOnly = false)
    }
}
