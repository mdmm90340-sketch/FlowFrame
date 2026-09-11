package com.flowframe.app

import android.app.Application
import com.flowframe.app.core.engine.DownloadEngine
import com.flowframe.app.data.DownloadRepository
import com.flowframe.app.data.AppSettingsStore
import com.flowframe.app.data.TaskStore
import com.flowframe.app.data.NetworkMonitor
import com.flowframe.app.worker.DownloadConcurrencyGate
import com.flowframe.app.worker.DownloadNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class FlowFrameApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.createChannel(this)
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val taskStore = TaskStore(application)
    val settingsStore = AppSettingsStore(application)
    val networkMonitor = NetworkMonitor(application, appScope)
    val engine = DownloadEngine(application)
    val downloadGate = DownloadConcurrencyGate {
        settingsStore.state.value.maxConcurrentDownloads
    }
    private val engineReady: Deferred<Unit> = appScope.async(Dispatchers.IO) {
        engine.initialize()
    }
    val repository = DownloadRepository(
        application,
        taskStore,
        engine,
        engineReady,
        settingsStore,
    )

    suspend fun awaitEngine() = engineReady.await()
}
