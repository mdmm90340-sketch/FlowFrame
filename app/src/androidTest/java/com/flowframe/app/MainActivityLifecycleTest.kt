package com.flowframe.app

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowframe.app.ui.model.FlowFrameDestination
import com.flowframe.app.ui.model.FlowFrameOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real Activity and ViewModel; no parsing or media network request is started. */
@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleTest {
    @Test
    fun recreationDoesNotReplayAnAlreadyConsumedShareIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, FIRST_LINK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
                assertEquals(FIRST_LINK, viewModel.uiState.value.home.linkText)
                viewModel.onLinkChanged("下一条作品的输入草稿")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
                assertEquals("下一条作品的输入草稿", viewModel.uiState.value.home.linkText)
            }
        }
    }

    @Test
    fun incomingShareAndDestinationActionsDismissAnExistingOverlay() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
                viewModel.onAboutRequested()
                assertEquals(FlowFrameOverlay.About, viewModel.uiState.value.overlay)
                viewModel.acceptSharedText(FIRST_LINK)
                assertNull(viewModel.uiState.value.overlay)
                assertEquals(FlowFrameDestination.Home, viewModel.uiState.value.selectedDestination)
                assertEquals(FIRST_LINK, viewModel.uiState.value.home.linkText)

                viewModel.onDiagnosticsRequested()
                assertEquals(FlowFrameOverlay.Diagnostics, viewModel.uiState.value.overlay)
                viewModel.onDestinationSelected(FlowFrameDestination.Tasks)
                assertNull(viewModel.uiState.value.overlay)
                assertEquals(FlowFrameDestination.Tasks, viewModel.uiState.value.selectedDestination)
            }
        }
    }

    private companion object {
        const val FIRST_LINK = "https://www.douyin.com/video/1234567890123456789"
    }
}
