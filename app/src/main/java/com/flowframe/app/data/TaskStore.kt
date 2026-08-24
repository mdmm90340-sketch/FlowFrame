package com.flowframe.app.data

import android.content.Context
import android.util.AtomicFile
import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.MediaKind
import com.flowframe.app.core.model.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class TaskStore(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val serializer = ListSerializer(DownloadTask.serializer())
    private val atomicFile = AtomicFile(File(context.filesDir, "download_tasks.json"))
    private val initialLoad = load()
    private val mutex = Mutex()
    private val _tasks = MutableStateFlow(initialLoad.tasks)

    init {
        if (initialLoad.needsRewrite) persist(initialLoad.tasks)
    }

    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    suspend fun add(task: DownloadTask) = mutex.withLock {
        if (_tasks.value.any { it.id == task.id }) return@withLock
        persist(listOf(task.withoutEphemeralUrls()) + _tasks.value)
    }

    suspend fun update(id: String, transform: (DownloadTask) -> DownloadTask) = mutex.withLock {
        var changed = false
        val updated = _tasks.value.map { task ->
            if (task.id == id) {
                changed = true
                transform(task).copy(updatedAtEpochMillis = System.currentTimeMillis())
            } else {
                task
            }
        }
        if (changed) persist(updated)
    }

    suspend fun remove(id: String) = mutex.withLock {
        persist(_tasks.value.filterNot { it.id == id })
    }

    private fun load(): LoadResult = runCatching {
        if (!atomicFile.baseFile.exists()) return@runCatching LoadResult(emptyList(), false)
        atomicFile.openRead().use { input ->
            val text = input.readBytes().toString(Charsets.UTF_8)
            val decoded = json.decodeFromString(serializer, text)
            val sanitized = decoded.map { it.withoutEphemeralUrls() }
            LoadResult(sanitized, sanitized != decoded)
        }
    }.getOrDefault(LoadResult(emptyList(), false))

    private fun persist(tasks: List<DownloadTask>) {
        val sanitized = tasks.map { it.withoutEphemeralUrls() }
        val output = atomicFile.startWrite()
        try {
            output.write(json.encodeToString(serializer, sanitized).toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
            _tasks.value = sanitized
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun DownloadTask.withoutEphemeralUrls(): DownloadTask =
        if (thumbnailUrl != null && (platform == Platform.DOUYIN || mediaKind == MediaKind.GALLERY)) {
            copy(thumbnailUrl = null)
        } else {
            this
        }

    private data class LoadResult(
        val tasks: List<DownloadTask>,
        val needsRewrite: Boolean,
    )
}
