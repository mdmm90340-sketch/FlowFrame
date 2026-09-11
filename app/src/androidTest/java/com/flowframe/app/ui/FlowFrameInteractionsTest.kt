package com.flowframe.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.flowframe.app.ui.model.DownloadTaskStage
import com.flowframe.app.ui.model.DownloadTaskUi
import com.flowframe.app.ui.model.FlowFrameDestination
import com.flowframe.app.ui.model.FlowFrameOverlay
import com.flowframe.app.ui.model.FlowFrameUiState
import com.flowframe.app.ui.model.GalleryOutputModeUi
import com.flowframe.app.ui.model.MediaKindUi
import com.flowframe.app.ui.model.MediaPlatform
import com.flowframe.app.ui.model.MediaPreviewUi
import com.flowframe.app.ui.model.SettingsUiState
import com.flowframe.app.ui.model.TaskAction
import com.flowframe.app.ui.model.TaskFilter
import com.flowframe.app.ui.model.TasksUiState
import com.flowframe.app.ui.model.ThemeMode
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FlowFrameInteractionsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeNamesItsPurposeAndPasteIsAnExplicitAction() {
        var pasted = false
        compose.setContent {
            FlowFrameApp(FlowFrameUiState(), callbacks = object : FlowFrameCallbacks {
                override fun onPasteRequested() { pasted = true }
            })
        }
        compose.onNodeWithText("视频与图集下载").assertIsDisplayed()
        compose.onNodeWithText("粘贴").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(true, pasted) }
    }

    @Test
    fun gallerySelectionChangesWhichOutputCanBeDownloaded() {
        val state = mutableStateOf(FlowFrameUiState(activePreview = gallery(selected = emptySet())))
        val callbacks = object : FlowFrameCallbacks {
            override fun onContentSelectionRequested() {
                state.value = state.value.copy(overlay = FlowFrameOverlay.Gallery)
            }
            override fun onSelectAllImages(selected: Boolean) {
                val preview = state.value.activePreview!!
                state.value = state.value.copy(activePreview = preview.copy(
                    selectedImageIndices = if (selected) preview.imageUrls.indices.toSet() else emptySet(),
                ))
            }
            override fun onImageSelectionChanged(index: Int, selected: Boolean) {
                val preview = state.value.activePreview!!
                state.value = state.value.copy(activePreview = preview.copy(
                    selectedImageIndices = if (selected) preview.selectedImageIndices + index
                    else preview.selectedImageIndices - index,
                ))
            }
            override fun onOverlayDismissed() { state.value = state.value.copy(overlay = null) }
        }
        compose.setContent { FlowFrameApp(state.value, callbacks = callbacks) }
        compose.onNodeWithTag("preview_download").assertIsNotEnabled()
        compose.onNodeWithTag("preview_gallery").performScrollTo().performClick()
        compose.onNodeWithTag("gallery_current_selection").assertIsOff().performClick()
        compose.onNodeWithTag("gallery_current_selection").assertIsOn()
        compose.onNodeWithTag("gallery_select_all").performClick()
        compose.runOnIdle { assertEquals(setOf(0, 1), state.value.activePreview!!.selectedImageIndices) }
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("preview_download").assertIsEnabled()
    }

    @Test
    fun systemBackClosesDetailsThenPreviewWithoutLeavingTheActivity() {
        val state = mutableStateOf(FlowFrameUiState(
            activePreview = gallery(),
            overlay = FlowFrameOverlay.FormatDetails,
        ))
        compose.setContent {
            FlowFrameApp(state.value, callbacks = object : FlowFrameCallbacks {
                override fun onOverlayDismissed() { state.value = state.value.copy(overlay = null) }
                override fun onPreviewBack() { state.value = state.value.copy(activePreview = null) }
            })
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("下载预览").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("视频与图集下载").assertIsDisplayed()
    }

    @Test
    fun diagnosticsShowsReadableDetailsAndCopiesOnRequest() {
        var copies = 0
        compose.setContent {
            FlowFrameApp(
                FlowFrameUiState(overlay = FlowFrameOverlay.Diagnostics, diagnosticsText = "测试设备\n任务：0"),
                callbacks = object : FlowFrameCallbacks {
                    override fun onCopyDiagnosticsRequested() { copies++ }
                },
            )
        }
        compose.onNodeWithText("测试设备\n任务：0").assertIsDisplayed()
        compose.onNodeWithText("复制诊断信息").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, copies) }
    }

    @Test
    fun unavailableDynamicColorIsDisabledAndDirectoryResetIsReal() {
        var resets = 0
        compose.setContent {
            FlowFrameApp(
                FlowFrameUiState(
                    selectedDestination = FlowFrameDestination.Settings,
                    settings = SettingsUiState(dynamicColorAvailable = false, customOutputDirectory = true),
                ),
                callbacks = object : FlowFrameCallbacks {
                    override fun onResetOutputDirectoryRequested() { resets++ }
                },
            )
        }
        compose.onNodeWithText("恢复系统默认目录").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, resets) }
        compose.onNodeWithContentDescription("系统动态配色").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cookie 与登录凭据").assertDoesNotExist()
    }

    @Test
    fun canceledTaskCanRetryAndRecordRemovalRequiresConfirmation() {
        val actions = mutableListOf<TaskAction>()
        compose.setContent {
            FlowFrameApp(
                FlowFrameUiState(
                    selectedDestination = FlowFrameDestination.Tasks,
                    tasks = TasksUiState(
                        selectedFilter = TaskFilter.Canceled,
                        items = listOf(DownloadTaskUi(
                            id = "test-task",
                            title = "已取消的测试作品",
                            platform = MediaPlatform.Weibo,
                            stage = DownloadTaskStage.Canceled,
                            formatLabel = "推荐",
                        )),
                    ),
                ),
                callbacks = object : FlowFrameCallbacks {
                    override fun onTaskAction(taskId: String, action: TaskAction) { actions += action }
                },
            )
        }
        compose.onNodeWithText("重试").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(TaskAction.Retry), actions) }
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除任务记录？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(TaskAction.Retry), actions) }
        compose.onNodeWithText("确认删除").performClick()
        compose.runOnIdle { assertEquals(listOf(TaskAction.Retry, TaskAction.Delete), actions) }
    }

    @Test
    fun manualThemeUpdatesStatusBarIconContrast() {
        val state = mutableStateOf(FlowFrameUiState(settings = SettingsUiState(themeMode = ThemeMode.Light)))
        compose.setContent { FlowFrameApp(state.value) }
        compose.runOnIdle {
            assertEquals(true, WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars)
            state.value = state.value.copy(settings = state.value.settings.copy(themeMode = ThemeMode.Dark))
        }
        compose.runOnIdle {
            assertEquals(false, WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars)
        }
    }

    private fun gallery(selected: Set<Int> = setOf(0, 1)) = MediaPreviewUi(
        id = "test-gallery",
        title = "测试图集",
        author = "测试作者",
        platform = MediaPlatform.Douyin,
        durationLabel = "",
        mediaKind = MediaKindUi.Gallery,
        imageCount = 2,
        imageUrls = listOf("", ""),
        selectedImageIndices = selected,
        selectedGalleryOutputMode = GalleryOutputModeUi.Images,
        canDownload = true,
    )
}
