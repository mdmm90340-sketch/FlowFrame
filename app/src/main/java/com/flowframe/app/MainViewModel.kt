package com.flowframe.app

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowframe.app.core.engine.BundledYtDlpInstaller
import com.flowframe.app.core.error.FailureClassifier
import com.flowframe.app.core.error.FailureOperation
import com.flowframe.app.core.model.DownloadTask
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
import com.flowframe.app.ui.model.DownloadTaskStage
import com.flowframe.app.ui.model.DownloadTaskUi
import com.flowframe.app.ui.model.FlowFrameDestination
import com.flowframe.app.ui.model.FlowFrameUiState
import com.flowframe.app.ui.model.FormatPresetUi
import com.flowframe.app.ui.model.GalleryOutputModeUi
import com.flowframe.app.ui.model.MediaKindUi
import com.flowframe.app.ui.model.MediaPlatform
import com.flowframe.app.ui.model.MediaPreviewUi
import com.flowframe.app.ui.model.ParseStage
import com.flowframe.app.ui.model.ParseUiState
import com.flowframe.app.ui.model.RecentMediaUi
import com.flowframe.app.ui.model.SettingsUiState
import com.flowframe.app.ui.model.TaskAction
import com.flowframe.app.ui.model.TaskFilter
import com.flowframe.app.ui.model.TaskOutputAction
import com.flowframe.app.ui.model.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.lang.Character

class MainViewModel(application: Application) : AndroidViewModel(application), FlowFrameCallbacks {
    private val container = (application as FlowFrameApplication).container
    private val repository = container.repository
    private val settingsStore = container.settingsStore
    private val previews = LinkedHashMap<String, MediaPreview>()
    private var activePreview: MediaPreview? = null
    private var parseJob: Job? = null
    private var parseGeneration = 0L
    private var activeParseProcessId: String? = null
    private var downloadJob: Job? = null

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
        viewModelScope.launch {
            repository.tasks.collect { tasks ->
                _uiState.update { state ->
                    state.copy(tasks = state.tasks.copy(items = tasks.map { it.toUi() }))
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
                        ),
                    )
                }
            }
        }
    }

    fun acceptSharedText(text: String) {
        cancelActiveParse()
        activePreview = null
        _uiState.update {
            it.copy(
                selectedDestination = FlowFrameDestination.Home,
                activePreview = null,
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
        _uiState.update { it.copy(selectedDestination = destination, activePreview = null) }
        activePreview = null
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
            showParseError("没有找到可识别的抖音或 B站链接")
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
                previews[preview.mediaId] = preview
                while (previews.size > 8) previews.remove(previews.keys.first())
                activePreview = preview
                _uiState.update { state ->
                    val recent = previews.values.toList().asReversed().take(3).map { it.toRecentUi() }
                    state.copy(
                        home = state.home.copy(parseState = ParseUiState.Idle, recentItems = recent),
                        activePreview = preview.toPreviewUi(),
                    )
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Throwable) {
                val failure = FailureClassifier.classify(error, FailureOperation.PARSE)
                Log.w(LOG_TAG, "Parse failed [${failure.kind}]: ${failure.diagnostic}")
                showParseError(failure.userMessage)
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
            state.copy(activePreview = preview.copy(audioOnly = enabled, canDownload = true))
        }
    }

    override fun onContentSelectionRequested() {
        emitMessage("视频按当前单条作品下载；图文会按原顺序处理全部图片。")
    }

    override fun onFormatDetailsRequested() {
        emitMessage("推荐、最高画质、节省空间和仅音频均由解析器按实际可用流自动匹配。")
    }

    override fun onDownloadRequested() {
        if (downloadJob?.isActive == true) return
        val preview = activePreview ?: return
        val previewUi = _uiState.value.activePreview ?: return
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
            runCatching { repository.enqueue(preview, preset, galleryMode) }
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
            TaskAction.Cancel -> viewModelScope.launch { repository.cancel(taskId) }
            TaskAction.Retry -> viewModelScope.launch { repository.retry(taskId) }
            TaskAction.Delete -> viewModelScope.launch { repository.remove(taskId) }
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
        emitMessage("视频保存到“影片/FlowFrame”，音频保存到“音乐/FlowFrame”，图片保存到“图片/FlowFrame”。")
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
        settingsStore.setDynamicColor(enabled)
    }

    override fun onDiagnosticsRequested() {
        val active = repository.tasks.value.count { it.stage in ACTIVE_STAGES }
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "未知" }
        emitMessage("解析内核：${BundledYtDlpInstaller.KERNEL_VERSION} · 活跃任务：$active · ABI：$abi")
    }

    override fun onAboutRequested() {
        emitMessage("流影 ${BuildConfig.VERSION_NAME} · GPL-3.0 开源软件 · 仅用于保存你有权下载的公开内容。")
    }

    private fun cancelActiveParse() {
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
            id = mediaId,
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
            selectedGalleryOutputMode = GalleryOutputModeUi.Mp4,
            description = if (gallery) {
                "可保存全部图片、原始配乐，或合成为 1080×1920 MP4。"
            } else {
                "下载前请确认你拥有保存和使用此内容的权利。"
            },
            contentCount = if (gallery) imageCount.coerceAtLeast(1) else 1,
            selectedContentCount = if (gallery) imageCount.coerceAtLeast(1) else 1,
            presets = if (gallery) emptyList() else videoPresets(),
            selectedPresetId = if (gallery) null else PRESET_RECOMMENDED,
            estimatedSizeLabel = estimatedSizeBytes.takeIf { it > 0 }?.formatBytes(),
            destinationLabel = if (gallery) "按所选方式保存到系统媒体库" else "影片 / FlowFrame",
            canDownload = if (gallery) imageCount > 0 else true,
        )
    }

    private fun videoPresets() = listOf(
        FormatPresetUi(
            id = PRESET_RECOMMENDED,
            title = "推荐",
            subtitle = "最高 1080P，优先兼容格式",
            detail = "MP4 · H.264 / AAC 优先",
            badge = "推荐",
        ),
        FormatPresetUi(
            id = PRESET_BEST,
            title = "最高画质",
            subtitle = "选择当前公开视频可用的最高质量",
            detail = "必要时自动合并音视频",
        ),
        FormatPresetUi(
            id = PRESET_DATA_SAVER,
            title = "节省空间",
            subtitle = "最高 720P，兼顾清晰与体积",
            detail = "适合移动网络与快速分享",
        ),
    )

    private fun MediaPreview.toRecentUi() = RecentMediaUi(
        id = mediaId,
        title = title,
        author = uploader ?: "未知作者",
        platform = platform.toUi(),
        metadata = if (mediaKind == MediaKind.GALLERY) {
            listOf("${imageCount}张", durationSeconds.durationLabel()).joinToString(" · ")
        } else {
            durationSeconds.durationLabel()
        },
    )

    private fun DownloadTask.toUi() = DownloadTaskUi(
        id = id,
        title = title,
        platform = platform.toUi(),
        stage = when (stage) {
            TaskStage.QUEUED -> DownloadTaskStage.Queued
            TaskStage.RESOLVING -> DownloadTaskStage.Resolving
            TaskStage.DOWNLOADING -> DownloadTaskStage.Downloading
            TaskStage.MERGING -> DownloadTaskStage.Merging
            TaskStage.COMPLETED -> DownloadTaskStage.Completed
            TaskStage.FAILED,
            TaskStage.CANCELED -> DownloadTaskStage.Failed
        },
        formatLabel = when (preset) {
            QualityPreset.RECOMMENDED -> "推荐"
            QualityPreset.BEST -> "最高画质"
            QualityPreset.DATA_SAVER -> "节省空间"
            QualityPreset.AUDIO_ONLY -> "仅音频"
        },
        mediaKind = mediaKind.toUi(),
        galleryOutputMode = galleryOutputMode?.toUi(),
        outputLocations = resolvedOutputLocations,
        progress = progress,
        downloadedBytes = outputSizeBytes,
        totalBytes = outputSizeBytes.takeIf { it > 0L },
        etaSeconds = etaSeconds,
        errorMessage = when {
            stage == TaskStage.CANCELED -> "任务已取消"
            else -> errorMessage
        },
    )

    private fun Platform.toUi() = when (this) {
        Platform.DOUYIN -> MediaPlatform.Douyin
        Platform.BILIBILI -> MediaPlatform.Bilibili
    }

    private fun Platform.inputLabel() = when (this) {
        Platform.DOUYIN -> "抖音"
        Platform.BILIBILI -> "B站"
    }

    private fun MediaKind.toUi() = when (this) {
        MediaKind.VIDEO -> MediaKindUi.Video
        MediaKind.GALLERY -> MediaKindUi.Gallery
    }

    private fun GalleryOutputMode.toUi() = when (this) {
        GalleryOutputMode.IMAGES -> GalleryOutputModeUi.Images
        GalleryOutputMode.AUDIO -> GalleryOutputModeUi.Audio
        GalleryOutputMode.MP4 -> GalleryOutputModeUi.Mp4
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
