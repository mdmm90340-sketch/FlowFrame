package com.flowframe.app.data

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.TaskTransitions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class TaskStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(DownloadTask.serializer())
    private val atomicFile = AtomicFile(File(context.filesDir, "download_tasks.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private var lastPersistAt = 0L
    private var dirty = false
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()
    var loadWarning: String? = null
        private set

    init {
        scope.launch {
            try {
                mutex.withLock {
                    if (atomicFile.baseFile.exists()) {
                        val loaded = atomicFile.openRead().use {
                            json.decodeFromString(serializer, it.readBytes().toString(Charsets.UTF_8))
                        }
                        val sanitized = loaded.map { it.withoutEphemeralUrls() }
                        _tasks.value = sanitized
                        if (sanitized != loaded) persist(sanitized)
                    }
                }
                ready.complete(Unit)
            } catch (error: Exception) {
                // Never silently overwrite an unreadable history file.
                loadWarning = "任务记录读取失败，原文件已保留，请导出诊断后重试"
                ready.completeExceptionally(error)
            }
        }
        scope.launch {
            while (true) {
                delay(PERSIST_INTERVAL_MS)
                mutex.withLock { if (dirty) runCatching { persist(_tasks.value) } }
            }
        }
    }

    suspend fun awaitLoaded() = ready.await()
    fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    suspend fun add(task: DownloadTask) = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            if (_tasks.value.none { it.id == task.id }) persist(listOf(task) + _tasks.value)
        }
    }

    suspend fun update(
        id: String,
        persistImmediately: Boolean = true,
        transform: (DownloadTask) -> DownloadTask,
    ): DownloadTask? = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            val current = find(id) ?: return@withLock null
            val proposed = transform(current).copy(updatedAtEpochMillis = System.currentTimeMillis())
            val changed = TaskTransitions.apply(current, proposed).withoutEphemeralUrls()
            if (changed == current) return@withLock current
            val updated = _tasks.value.map { if (it.id == id) changed else it }
            if (persistImmediately || SystemClock.elapsedRealtime() - lastPersistAt >= PERSIST_INTERVAL_MS) {
                persist(updated)
            } else {
                _tasks.value = updated
                dirty = true
            }
            changed
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock { persist(_tasks.value.filterNot { it.id == id }) }
    }

    private fun persist(tasks: List<DownloadTask>) {
        val sanitized = tasks.map { it.withoutEphemeralUrls() }
        val output = atomicFile.startWrite()
        try {
            output.write(json.encodeToString(serializer, sanitized).toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
            _tasks.value = sanitized
            dirty = false
            lastPersistAt = SystemClock.elapsedRealtime()
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }
    private fun DownloadTask.withoutEphemeralUrls(): DownloadTask = copy(thumbnailUrl = null)
    private companion object { const val PERSIST_INTERVAL_MS = 1_500L }
}
