package com.flowframe.app.data

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.TaskTransitions
import com.flowframe.app.core.model.TaskStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class TaskStore internal constructor(
    context: Context,
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long,
) {
    constructor(context: Context) : this(
        context, CoroutineScope(SupervisorJob() + Dispatchers.IO), SystemClock::elapsedRealtime,
    )
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(DownloadTask.serializer())
    private val atomicFile = AtomicFile(File(context.filesDir, "download_tasks.json"))
    private val ready = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private var lastPersistAt = 0L
    private var dirty = false
    private var pendingFlush: Job? = null
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
    }

    suspend fun awaitLoaded() = ready.await()
    fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    suspend fun add(task: DownloadTask) = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            if (_tasks.value.none { it.id == task.id }) persist(listOf(task.withoutEphemeralUrls()) + _tasks.value)
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
            val proposed = TaskTransitions.apply(current, transform(current)).withoutEphemeralUrls()
            if (proposed == current) return@withLock current
            val changed = proposed.copy(updatedAtEpochMillis = System.currentTimeMillis())
            val updated = _tasks.value.map { if (it.id == id) changed else it }
            val terminal = when (changed.stage) {
                TaskStage.COMPLETED, TaskStage.CANCELED, TaskStage.FAILED -> true
                else -> false
            }
            if (persistImmediately || terminal || elapsedRealtime() - lastPersistAt >= PERSIST_INTERVAL_MS) {
                persist(updated)
            } else {
                _tasks.value = updated
                dirty = true
                scheduleFlush()
            }
            changed
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            if (_tasks.value.any { it.id == id }) persist(_tasks.value.filterNot { it.id == id })
        }
    }

    /** Called with mutex held; at most one delayed write exists while the snapshot is dirty. */
    private fun scheduleFlush(afterFailure: Boolean = false) {
        if (!dirty || pendingFlush != null) return
        val waitMillis = if (afterFailure) PERSIST_INTERVAL_MS else
            (PERSIST_INTERVAL_MS - (elapsedRealtime() - lastPersistAt)).coerceAtLeast(1L)
        pendingFlush = scope.launch {
            delay(waitMillis)
            mutex.withLock {
                pendingFlush = null
                if (dirty) {
                    try {
                        persist(_tasks.value)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        // Retain the latest snapshot and wait a full window before another attempt.
                        scheduleFlush(afterFailure = true)
                    }
                }
            }
        }
    }

    private fun persist(tasks: List<DownloadTask>) {
        // Loading, adding and transforming records sanitize once at their entry points.
        val output = atomicFile.startWrite()
        try {
            output.write(json.encodeToString(serializer, tasks).toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
            _tasks.value = tasks
            dirty = false
            lastPersistAt = elapsedRealtime()
            pendingFlush?.cancel()
            pendingFlush = null
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }
    private fun DownloadTask.withoutEphemeralUrls(): DownloadTask =
        if (thumbnailUrl == null) this else copy(thumbnailUrl = null)
    private companion object { const val PERSIST_INTERVAL_MS = 1_500L }
}
