package com.flowframe.app

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.flowframe.app.core.engine.BundledYtDlpInstaller
import com.flowframe.app.core.error.FailureClassifier
import com.flowframe.app.core.error.FailureOperation
import com.flowframe.app.core.model.GalleryOutputMode
import com.flowframe.app.core.model.MediaKind
import com.flowframe.app.core.model.MediaPreview
import com.flowframe.app.core.model.Platform
import com.flowframe.app.core.model.QualityPreset
import com.flowframe.app.core.model.TaskStage
import com.flowframe.app.core.url.SupportedUrlParser
import com.flowframe.app.core.url.InputNormalizationResult
import com.flowframe.app.data.ThemePreference
import com.flowframe.app.ui.FlowFrameCallbacks
import com.flowframe.app.ui.model.FlowFrameDestination
import com.flowframe.app.ui.model.FlowFrameUiState
import com.flowframe.app.ui.model.FormatPresetUi
import com.flowframe.app.ui.model.GalleryOutputModeUi
import com.flowframe.app.ui.model.MediaKindUi
import com.flowframe.app.ui.model.MediaPreviewUi
import com.flowframe.app.ui.model.ParseStage
import com.flowframe.app.ui.model.ParseUiState
import com.flowframe.app.ui.model.RecentMediaUi
import com.flowframe.app.ui.model.SettingsUiState
import com.flowframe.app.ui.model.TaskAction
import com.flowframe.app.ui.model.TaskFilter
import com.flowframe.app.ui.model.TaskOutputAction
import com.flowframe.app.ui.model.ThemeMode
import com.flowframe.app.ui.model.FlowFrameOverlay
import com.flowframe.app.ui.model.TaskNetworkConstraints
import com.flowframe.app.ui.model.TaskUiProjector
import com.flowframe.app.ui.model.editorRecoverySnapshot
import com.flowframe.app.ui.model.toUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.lang.Character

class MainViewModel(application: Application, private val savedState: SavedStateHandle) :
    AndroidViewModel(application), FlowFrameCallbacks {
    private val container = (application as FlowFrameApplication).container
    private val repository = container.repository
    private val settingsStore = container.settingsStore
    private val previews = LinkedHashMap<String, MediaPreview>()
    private val previewThumbnails = MutableStateFlow<Map<String, String>>(emptyMap())
    private var activePreview: MediaPreview? = null
    private var parseJob: Job? = null
    private var parseGeneration = 0L
    private var activeParseProcessId: String? = null
    private var downloadJob: Job? = null
    private var restoringPreview: Boolean = savedState.get<String>("preview_url") != null
    private val restoredImageSelection = savedState.get<ArrayList<Int>>("selected_images")?.toSet()
    private val restoredPreset = savedState.get<String>("preset")
    private val restoredAudioOnly = savedState.get<Boolean>("audio_only") ?: false
    private val restoredGalleryMode = savedState.get<String>("gallery_mode")

    private val _uiState = MutableStateFlow(
        FlowFrameUiState(
            settings = SettingsUiState(
                outputDirectoryLabel = "系统影片 / FlowFrame",
                versionLabel = BuildConfig.VERSION_NAME,
            ),
        ),
    )
    val uiState: StateFlow<FlowFrameUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        val restoredUrl = savedState.get<String>("preview_url")
        val restoredInput = restoredUrl ?: savedState.get<String>("input").orEmpty()
        applyNormalizedInput(restoredInput)
        savedState.get<String>("destination")?.let { name ->
            FlowFrameDestination.entries.firstOrNull { it.name == name }?.let { destination ->
                _uiState.update { it.copy(selectedDestination = destination) }
            }
        }
        viewModelScope.launch {
            _uiState.map { state ->
                state.editorRecoverySnapshot(activePreview?.sourceUrl, restoringPreview)
            }.distinctUntilChanged().collect { snapshot ->
                savedState["input"] = snapshot.input
                savedState["destination"] = snapshot.destination
                snapshot.preview?.let { preview ->
                    savedState["preview_url"] = preview.url
                    savedState["selected_images"] = preview.selectedImages?.let { ArrayList(it) }
                    savedState["preset"] = preview.preset
                    savedState["audio_only"] = preview.audioOnly
                    savedState["gallery_mode"] = preview.galleryMode
                }
            }
        }
        viewModelScope.launch {
            val projector = TaskUiProjector()
            val networkConstraints = combine(
                container.networkMonitor.state,
                settingsStore.state.map { it.wifiOnly }.distinctUntilChanged(),
            ) { network, wifiOnly ->
                TaskNetworkConstraints(network.connected, network.wifi, wifiOnly)
            }.distinctUntilChanged()
            combine(repository.tasks, networkConstraints, previewThumbnails) { tasks, network, thumbnails ->
                network to projector.project(tasks, network, thumbnails)
            }.collect { (network, rows) ->
                _uiState.update { state ->
                    state.copy(tasks = state.tasks.copy(items = rows, isOffline = !network.connected))
                }
            }
        }
        viewModelScope.launch {
            settingsStore.state.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        settings = state.settings.copy(
                            wifiOnly = settings.wifiOnly,
                            maxConcurrentDownloads = settings.maxConcurrentDownloads,
                            themeMode = settings.theme.toUi(),
                            dynamicColor = settings.dynamicColor,
                            dynamicColorAvailable = Build.VERSION.SDK_INT >= 31,
                            customOutputDirectory = settings.outputDirectoryUri != null,
                            outputDirectoryLabel = settings.outputDirectoryName ?: defaultDirectoryLabel(),
                            outputDirectoryAvailable = directoryAvailable(settings.outputDirectoryUri),
                        ),
                        activePreview = state.activePreview?.copy(
                            destinationLabel = settings.outputDirectoryName ?: defaultDirectoryLabel(),
                        ),
                    )
                }
            }
        }
        if (restoredUrl != null) onParseRequested()
    }

    private fun defaultDirectoryLabel() = if (Build.VERSION.SDK_INT >= 29) {
        "系统影片、音乐或图片 / FlowFrame"
    } else "应用媒体目录 / FlowFrame"

    private fun directoryAvailable(uri: String?): Boolean = uri == null ||
        getApplication<Application>().contentResolver.persistedUriPermissions.any {
            it.uri.toString() == uri && it.isWritePermission && it.isReadPermission
        }

    fun refreshOutputDirectory() {
        _uiState.update { state -> state.copy(settings = state.settings.copy(
            outputDirectoryAvailable = directoryAvailable(settingsStore.state.value.outputDirectoryUri),
        )) }
    }

    fun setOutputDirectory(uri: String, name: String) { settingsStore.setOutputDirectory(uri, name) }

    fun acceptSharedText(text: String) {
        cancelActiveParse()
        activePreview = null
        _uiState.update {
            it.copy(
                selectedDestination = FlowFrameDestination.Home,
                activePreview = null,
                overlay = null,
            )
        }
        applyNormalizedInput(text)
    }

    fun setClipboardAvailability(available: Boolean) {
        _uiState.update { state ->
            state.copy(home = state.home.copy(canPaste = available))
        }
    }

    override fun onDestinationSelected(destination: FlowFrameDestination) {
        cancelActiveParse()
        activePreview = null
        _uiState.update { state ->
            state.copy(
                selectedDestination = destination,
                activePreview = null,
                overlay = null,
                home = state.home.copy(parseState = ParseUiState.Idle),
            )
        }
    }

    override fun onLinkChanged(value: String) {
        cancelActiveParse()
        applyNormalizedInput(value)
    }

    override fun onClearLinkRequested() {
        onLinkChanged("")
    }

    override fun onParseRequested() {
        if (parseJob?.isActive == true) return
        val input = _uiState.value.home.linkText
        val supported = SupportedUrlParser.extract(input)
        if (supported == null) {
            showParseError("没有找到支持的作品链接，请粘贴完整分享文案")
            return
        }
        val expectedUrl = supported.value
        val generation = ++parseGeneration
        val parseProcessId = "${com.flowframe.app.data.DownloadRepository.PARSE_PROCESS_ID}-$generation"
        activeParseProcessId = parseProcessId
        _uiState.update { state ->
            state.copy(home = state.home.copy(parseState = ParseUiState.Loading(ParseStage.Recognizing)))
        }
        parseJob = viewModelScope.launch {
            try {
                _uiState.update { state ->
                    state.copy(home = state.home.copy(parseState = ParseUiState.Loading(ParseStage.FetchingMetadata)))
                }
                val preview = repository.parse(expectedUrl, parseProcessId)
                if (parseGeneration != generation || _uiState.value.home.linkText != expectedUrl) return@launch
                previews.remove(preview.stableKey)
                previews[preview.stableKey] = preview
                while (previews.size > 8) previews.remove(previews.keys.first())
                previewThumbnails.value = previews.mapNotNull { (key, value) ->
                    value.thumbnailUrl?.let { key to it }
                }.toMap()
                activePreview = preview
                _uiState.update { state ->
                    val recent = previews.values.toList().asReversed().take(3).map { it.toRecentUi() }
                    state.copy(
                        home = state.home.copy(parseState = ParseUiState.Idle, recentItems = recent),
                        activePreview = preview.toPreviewUi().let { ui ->
                            if (!restoringPreview) ui else ui.copy(
                                selectedImageIndices = restoredImageSelection?.filter { it in ui.imageUrls.indices }?.toSet()
                                    ?: ui.selectedImageIndices,
                                selectedPresetId = restoredPreset?.takeIf { id -> ui.presets.any { it.id == id } }
                                    ?: ui.selectedPresetId,
                                audioOnly = restoredAudioOnly && ui.hasAudio,
                                selectedGalleryOutputMode = GalleryOutputModeUi.entries.firstOrNull {
                                    it.name == restoredGalleryMode && ui.galleryOutputOptions.any { opt -> opt.mode == it && opt.available }
                                } ?: ui.selectedGalleryOutputMode,
                            )
                        },
                    )
                }
                restoringPreview = false
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Throwable) {
                if (parseGeneration != generation) return@launch
                val failure = FailureClassifier.classify(error, FailureOperation.PARSE)
                Log.w(LOG_TAG, "Parse failed [${failure.kind}]: ${failure.diagnostic}")
                showParseError(failure.userMessage)
                restoringPreview = false
            } finally {
                repository.cancelProcess(parseProcessId)
                if (parseGeneration == generation) {
                    parseJob = null
                    activeParseProcessId = null
                }
            }
        }
    }

    override fun onParseErrorAction() {
        onParseRequested()
    }

    override fun onRecentMediaSelected(mediaId: String) {
        val preview = previews[mediaId] ?: return
        activePreview = preview
        _uiState.update { it.copy(activePreview = preview.toPreviewUi()) }
    }

    override fun onPreviewBack() {
        activePreview = null
        _uiState.update { it.copy(activePreview = null) }
    }

    override fun onGalleryOutputModeSelected(mode: GalleryOutputModeUi) {
        _uiState.update { state ->
            val preview = state.activePreview ?: return@update state
            if (preview.mediaKind != MediaKindUi.Gallery) return@update state
            val available = preview.galleryOutputOptions.any { it.mode == mode && it.available }
            if (!available) return@update state
            state.copy(
                activePreview = preview.copy(
                    selectedGalleryOutputMode = mode,
                    canDownload = true,
                ),
            )
        }
    }

    override fun onPresetSelected(presetId: String) {
        _uiState.update { state ->
            val preview = state.activePreview ?: return@update state
            state.copy(
                activePreview = preview.copy(
                    selectedPresetId = presetId,
                    audioOnly = false,
                    canDownload = preview.presets.any { it.id == presetId && it.available },
                ),
            )
        }
    }

    override fun onAudioOnlyChanged(enabled: Boolean) {
        _uiState.update { state ->
            val preview = state.activePreview ?: return@update state
            if (enabled && !preview.hasAudio) return@update state
            state.copy(activePreview = preview.copy(audioOnly = enabled, canDownload = true))
        }
    }

    override fun onContentSelectionRequested() {
        if (_uiState.value.activePreview?.mediaKind == MediaKindUi.Gallery) {
            _uiState.update { it.copy(overlay = FlowFrameOverlay.Gallery) }
        }
    }

    override fun onFormatDetailsRequested() {
        _uiState.update { it.copy(overlay = FlowFrameOverlay.FormatDetails) }
    }

    override fun onImageSelectionChanged(index: Int, selected: Boolean) {
        _uiState.update { state ->
            val preview = state.activePreview ?: return@update state
            if (index !in preview.imageUrls.indices) return@update state
            val indices = if (selected) preview.selectedImageIndices + index else preview.selectedImageIndices - index
            state.copy(activePreview = preview.copy(selectedImageIndices = indices, selectedContentCount = indices.size))
        }
    }

    override fun onSelectAllImages(selected: Boolean) {
        _uiState.update { state ->
            val preview = state.activePreview ?: return@update state
            val indices = if (selected) preview.imageUrls.indices.toSet() else emptySet()
            state.copy(activePreview = preview.copy(selectedImageIndices = indices, selectedContentCount = indices.size))
        }
    }

    override fun onOverlayDismissed() { _uiState.update { it.copy(overlay = null) } }
    override fun onCopyDiagnosticsRequested() { _events.tryEmit(UiEvent.CopyText(_uiState.value.diagnosticsText)) }
    override fun onOpenRepositoryRequested() {
        _events.tryEmit(UiEvent.OpenUrl("https://github.com/mdmm90340-sketch/FlowFrame"))
    }
    override fun onResetOutputDirectoryRequested() { settingsStore.setOutputDirectory(null, null) }

    override fun onDownloadRequested() {
        if (downloadJob?.isActive == true) return
        val preview = activePreview ?: return
        val previewUi = _uiState.value.activePreview ?: return
        if (!previewUi.canDownload || !previewUi.selectedOutputAvailable) return
        if (!directoryAvailable(settingsStore.state.value.outputDirectoryUri)) {
            emitMessage("保存目录授权已失效，请重新选择目录")
            onOutputDirectoryRequested()
            return
        }
        val preset = if (preview.mediaKind == MediaKind.GALLERY) {
            QualityPreset.RECOMMENDED
        } else if (previewUi.audioOnly) {
            QualityPreset.AUDIO_ONLY
        } else {
            previewUi.selectedPresetId.toPreset()
        }
        val galleryMode = if (preview.mediaKind == MediaKind.GALLERY) {
            previewUi.selectedGalleryOutputMode.toData()
        } else {
            null
        }
        _uiState.update { state ->
            state.copy(activePreview = state.activePreview?.copy(canDownload = false))
        }
        downloadJob = viewModelScope.launch {
            runCatching { repository.enqueue(preview, preset, galleryMode,
                selectedImageIndices = if (preview.mediaKind == MediaKind.GALLERY) previewUi.selectedImageIndices.sorted() else null,
            ) }
                .onSuccess {
                    activePreview = null
                    _uiState.update { state ->
                        state.copy(
                            activePreview = null,
                            selectedDestination = FlowFrameDestination.Tasks,
                        )
                    }
                    emitMessage("已加入下载任务")
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(activePreview = state.activePreview?.copy(canDownload = true))
                    }
                    emitMessage(error.message ?: "无法创建下载任务")
                }
        }
    }

    override fun onTaskFilterSelected(filter: TaskFilter) {
        _uiState.update { it.copy(tasks = it.tasks.copy(selectedFilter = filter)) }
    }

    override fun onTaskAction(taskId: String, action: TaskAction) {
        val task = repository.tasks.value.firstOrNull { it.id == taskId }
        when (action) {
            TaskAction.Cancel -> performTaskAction { repository.cancel(taskId) }
            TaskAction.Retry -> performTaskAction { repository.retry(taskId) }
            TaskAction.Delete -> performTaskAction { repository.remove(taskId) }
            TaskAction.Open -> task?.resolvedOutputLocations?.firstOrNull()
                ?.let { _events.tryEmit(UiEvent.OpenMedia(it)) }
                ?: emitMessage("输出文件尚不可用")
            TaskAction.Share -> task?.resolvedOutputLocations?.takeIf(List<String>::isNotEmpty)
                ?.let { _events.tryEmit(UiEvent.ShareMedia(it)) }
                ?: emitMessage("输出文件尚不可用")
            TaskAction.Pause,
            TaskAction.Resume -> emitMessage("当前解析引擎暂不支持安全暂停，可取消后重新下载。")
        }
    }

    private fun performTaskAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() } catch (canceled: CancellationException) { throw canceled }
            catch (error: Exception) { emitMessage("任务操作失败，请重试或查看诊断") }
        }
    }

    override fun onTaskOutputAction(taskId: String, action: TaskOutputAction) {
        val task = repository.tasks.value.firstOrNull { it.id == taskId }
        val outputs = task?.resolvedOutputLocations.orEmpty()
        when (action) {
            is TaskOutputAction.Open -> outputs.getOrNull(action.outputIndex)
                ?.let { _events.tryEmit(UiEvent.OpenMedia(it)) }
                ?: emitMessage("输出文件尚不可用")

            is TaskOutputAction.Share -> {
                val selected = action.outputIndexes?.mapNotNull(outputs::getOrNull) ?: outputs
                selected.takeIf(List<String>::isNotEmpty)
                    ?.let { _events.tryEmit(UiEvent.ShareMedia(it)) }
                    ?: emitMessage("输出文件尚不可用")
            }
        }
    }

    override fun onOutputDirectoryRequested() {
        _events.tryEmit(UiEvent.ChooseOutputDirectory(settingsStore.state.value.outputDirectoryUri))
    }

    override fun onWifiOnlyChanged(enabled: Boolean) {
        settingsStore.setWifiOnly(enabled)
    }

    override fun onMaxConcurrentDownloadsChanged(count: Int) {
        settingsStore.setMaxConcurrentDownloads(count)
    }

    override fun onCredentialsRequested() {
        emitMessage("为减少隐私风险，当前版本不读取账号或 Cookie，仅处理公开内容。")
    }

    override fun onThemeModeChanged(mode: ThemeMode) {
        settingsStore.setTheme(mode.toData())
    }

    override fun onDynamicColorChanged(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) settingsStore.setDynamicColor(enabled)
    }

    override fun onDiagnosticsRequested() {
        val active = repository.tasks.value.count { it.stage in ACTIVE_STAGES }
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "未知" }
        val network = container.networkMonitor.state.value
        val diagnostic = buildString {
            appendLine("视频与图集下载 · 流影 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT} · $abi")
            appendLine("解析内核：${BundledYtDlpInstaller.KERNEL_VERSION}")
            appendLine("网络：${if (!network.connected) "离线" else if (network.wifi) "Wi-Fi" else "其他网络"}")
            appendLine("仅 Wi-Fi：${settingsStore.state.value.wifiOnly} · 并发：${settingsStore.state.value.maxConcurrentDownloads}")
            appendLine("任务：${repository.tasks.value.size} · 进行中：$active")
            appendLine("保存目录：${if (settingsStore.state.value.outputDirectoryUri == null) "系统默认" else "用户选择"}")
            appendLine("目录授权：${if (directoryAvailable(settingsStore.state.value.outputDirectoryUri)) "有效" else "需重新授权"}")
            container.taskStore.loadWarning?.let { appendLine(it) }
            append("诊断不包含分享链接、作者、作品标题或账号信息。")
        }
        _uiState.update { it.copy(overlay = FlowFrameOverlay.Diagnostics, diagnosticsText = diagnostic) }
    }

    override fun onAboutRequested() {
        _uiState.update { it.copy(overlay = FlowFrameOverlay.About) }
    }

    private fun cancelActiveParse() {
        restoringPreview = false
        parseGeneration += 1
        parseJob?.cancel()
        parseJob = null
        activeParseProcessId?.let(repository::cancelProcess)
        activeParseProcessId = null
    }

    private fun applyNormalizedInput(rawValue: String) {
        if (rawValue.toByteArray(Charsets.UTF_8).size > SupportedUrlParser.MAX_INPUT_BYTES) {
            _uiState.update { state ->
                state.copy(
                    home = state.home.copy(
                        linkText = rawValue.takeUtf8Bytes(SupportedUrlParser.MAX_INPUT_BYTES),
                        detectedPlatform = null,
                        canParse = false,
                        inputMessage = "输入内容不能超过 16 KiB，请只保留一条分享文案",
                        inputMessageIsError = true,
                        parseState = ParseUiState.Idle,
                    ),
                )
            }
            return
        }
        when (val result = SupportedUrlParser.normalize(rawValue)) {
            InputNormalizationResult.NoSupportedLink -> _uiState.update { state ->
                state.copy(
                    home = state.home.copy(
                        linkText = rawValue,
                        detectedPlatform = null,
                        canParse = false,
                        inputMessage = null,
                        inputMessageIsError = false,
                        parseState = ParseUiState.Idle,
                    ),
                )
            }

            is InputNormalizationResult.SingleSupportedLink -> {
                val extracted = rawValue.trim() != result.link.value
                _uiState.update { state ->
                    state.copy(
                        home = state.home.copy(
                            linkText = result.link.value,
                            detectedPlatform = result.link.platform.toUi(),
                            canParse = true,
                            inputMessage = if (extracted) {
                                "已从分享文案提取${result.link.platform.inputLabel()}链接"
                            } else {
                                null
                            },
                            inputMessageIsError = false,
                            parseState = ParseUiState.Idle,
                        ),
                    )
                }
            }

            is InputNormalizationResult.MultipleSupportedLinks -> _uiState.update { state ->
                state.copy(
                    home = state.home.copy(
                        linkText = rawValue,
                        detectedPlatform = null,
                        canParse = false,
                        inputMessage = "检测到多个支持链接，请只保留一个",
                        inputMessageIsError = true,
                        parseState = ParseUiState.Idle,
                    ),
                )
            }
        }
    }

    private fun showParseError(message: String) {
        _uiState.update { state ->
            state.copy(
                home = state.home.copy(
                    parseState = ParseUiState.Error(message = message),
                ),
            )
        }
    }

    private fun emitMessage(message: String) {
        _events.tryEmit(UiEvent.Message(message))
    }

    private fun MediaPreview.toPreviewUi(): MediaPreviewUi {
        val gallery = mediaKind == MediaKind.GALLERY
        return MediaPreviewUi(
            id = stableKey,
            title = title,
            author = uploader ?: "未知作者",
            platform = platform.toUi(),
            durationLabel = durationSeconds.durationLabel(),
            metadataLabel = if (gallery) "图文作品 · 公开内容" else "公开内容",
            width = width,
            height = height,
            mediaKind = mediaKind.toUi(),
            imageCount = imageCount,
            hasAudio = hasAudio,
            thumbnailUrl = thumbnailUrl,
            imageUrls = imageUrls,
            selectedImageIndices = imageUrls.indices.toSet(),
            formatDetails = if (gallery) "图片按所选顺序保存。合成视频使用 H.264 / AAC、1080×1920；无配乐时生成静音视频。" else
                formats.joinToString("\n\n") { f ->
                    listOf(f.ext.uppercase(), "${f.width}×${f.height}", f.videoCodec ?: "无视频",
                        f.audioCodec ?: "无音频", f.filesizeBytes.takeIf { it > 0 }?.formatBytes().orEmpty())
                        .filter(String::isNotBlank).joinToString(" · ")
                }.ifBlank { "来源未提供完整编码信息；下载前将重新匹配实际可用媒体流。" },
            selectedGalleryOutputMode = GalleryOutputModeUi.Mp4,
            description = if (gallery) {
                "选择需要的图片，保存原图或合成 MP4。"
            } else {
                "下载前请确认你拥有保存和使用此内容的权利。"
            },
            contentCount = if (gallery) imageCount.coerceAtLeast(1) else 1,
            selectedContentCount = if (gallery) imageCount.coerceAtLeast(1) else 1,
            presets = if (gallery) emptyList() else videoPresets(this),
            selectedPresetId = if (gallery) null else PRESET_RECOMMENDED,
            estimatedSizeLabel = estimatedSizeBytes.takeIf { it > 0 }?.formatBytes(),
            destinationLabel = settingsStore.state.value.outputDirectoryName ?: defaultDirectoryLabel(),
            canDownload = if (gallery) imageCount > 0 else true,
        )
    }

    private fun videoPresets(preview: MediaPreview): List<FormatPresetUi> {
        val resolutions = preview.formats.filter { it.videoCodec != "none" && it.width > 0 && it.height > 0 }
            .map { minOf(it.width, it.height) }.distinct().sorted()
        if (resolutions.size <= 1) return listOf(FormatPresetUi(
            id = PRESET_RECOMMENDED,
            title = "保存视频",
            subtitle = resolutions.singleOrNull()?.let { "当前可用 ${it}P" } ?: "使用来源提供的视频质量",
            detail = "保留可用视频，必要时合并音轨",
        ))
        val recommended = resolutions.lastOrNull { it <= 1080 } ?: resolutions.first()
        val compact = resolutions.lastOrNull { it <= 720 } ?: resolutions.first()
        return listOf(
            FormatPresetUi(PRESET_RECOMMENDED, "推荐", "优先 ${recommended}P", "优先常见 MP4 编码", badge = "推荐"),
            FormatPresetUi(PRESET_BEST, "最高画质", "当前最高 ${resolutions.last()}P", "必要时合并音视频"),
            FormatPresetUi(PRESET_DATA_SAVER, "节省空间", "优先 ${compact}P", "降低体积，便于保存与分享"),
        )
    }

    private fun MediaPreview.toRecentUi() = RecentMediaUi(
        id = stableKey,
        thumbnailUrl = thumbnailUrl,
        title = title,
        author = uploader ?: "未知作者",
        platform = platform.toUi(),
        metadata = if (mediaKind == MediaKind.GALLERY) {
            listOf("${imageCount}张", durationSeconds.durationLabel()).joinToString(" · ")
        } else {
            durationSeconds.durationLabel()
        },
    )

    private fun Platform.inputLabel() = when (this) {
        Platform.DOUYIN -> "抖音"
        Platform.BILIBILI -> "B站"
        Platform.XIAOHONGSHU -> "小红书"
        Platform.WEIBO -> "微博"
        Platform.KUAISHOU -> "快手"
    }

    private fun GalleryOutputModeUi.toData() = when (this) {
        GalleryOutputModeUi.Images -> GalleryOutputMode.IMAGES
        GalleryOutputModeUi.Audio -> GalleryOutputMode.AUDIO
        GalleryOutputModeUi.Mp4 -> GalleryOutputMode.MP4
    }

    private fun ThemePreference.toUi() = when (this) {
        ThemePreference.SYSTEM -> ThemeMode.System
        ThemePreference.LIGHT -> ThemeMode.Light
        ThemePreference.DARK -> ThemeMode.Dark
    }

    private fun ThemeMode.toData() = when (this) {
        ThemeMode.System -> ThemePreference.SYSTEM
        ThemeMode.Light -> ThemePreference.LIGHT
        ThemeMode.Dark -> ThemePreference.DARK
    }

    private fun String?.toPreset() = when (this) {
        PRESET_BEST -> QualityPreset.BEST
        PRESET_DATA_SAVER -> QualityPreset.DATA_SAVER
        else -> QualityPreset.RECOMMENDED
    }

    private fun Int.durationLabel(): String = when {
        this <= 0 -> "时长未知"
        this < 60 -> "${this}秒"
        else -> String.format(Locale.CHINA, "%d:%02d", this / 60, this % 60)
    }

    private fun Long.formatBytes(): String = when {
        this < 1_024L * 1_024L -> "%.1f KB".format(Locale.CHINA, this / 1_024.0)
        this < 1_024L * 1_024L * 1_024L -> "%.1f MB".format(Locale.CHINA, this / 1_024.0 / 1_024.0)
        else -> "%.2f GB".format(Locale.CHINA, this / 1_024.0 / 1_024.0 / 1_024.0)
    }

    private fun String.takeUtf8Bytes(limit: Int): String {
        val result = StringBuilder()
        var index = 0
        var used = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            val value = String(Character.toChars(codePoint))
            val bytes = value.toByteArray(Charsets.UTF_8).size
            if (used + bytes > limit) break
            result.append(value)
            used += bytes
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }

    sealed interface UiEvent {
        data class Message(val text: String) : UiEvent
        data class OpenMedia(val location: String) : UiEvent
        data class ShareMedia(val locations: List<String>) : UiEvent
        data class ChooseOutputDirectory(val currentUri: String?) : UiEvent
        data class CopyText(val text: String) : UiEvent
        data class OpenUrl(val url: String) : UiEvent
    }

    private companion object {
        const val LOG_TAG = "FlowFrameParse"
        const val PRESET_RECOMMENDED = "recommended"
        const val PRESET_BEST = "best"
        const val PRESET_DATA_SAVER = "data_saver"
        val ACTIVE_STAGES = setOf(
            TaskStage.QUEUED,
            TaskStage.RESOLVING,
            TaskStage.DOWNLOADING,
            TaskStage.MERGING,
        )
    }
}
