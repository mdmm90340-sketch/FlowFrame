package com.flowframe.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flowframe.app.ui.FlowFrameApp
import com.flowframe.app.ui.FlowFrameCallbacks
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val folder = DocumentFile.fromTreeUri(this@MainActivity, uri)
                    require(folder != null && folder.canWrite()) { "所选目录无法写入" }
                    folder.name ?: "自选保存目录"
                }
            }
            result.onSuccess { viewModel.setOutputDirectory(uri.toString(), it) }
                .onFailure { Toast.makeText(this@MainActivity, "无法保留目录授权，请选择本机可写入的目录", Toast.LENGTH_LONG).show() }
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val callbacks: FlowFrameCallbacks by lazy {
        object : FlowFrameCallbacks by viewModel {
            override fun onPasteRequested() {
                pasteFromClipboard()
            }

            override fun onDownloadRequested() {
                viewModel.onDownloadRequested()
                requestNotificationPermissionIfNeeded()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            FlowFrameApp(uiState = uiState, callbacks = callbacks)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOutputDirectory()
        val clipboard = getSystemService(ClipboardManager::class.java)
        val hasText = clipboard.hasPrimaryClip() &&
            clipboard.primaryClipDescription?.hasMimeType("text/*") == true
        viewModel.setClipboardAvailability(hasText)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            extractSharedText(intent)?.let(viewModel::acceptSharedText)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_TASKS, false) == true) {
            viewModel.onDestinationSelected(com.flowframe.app.ui.model.FlowFrameDestination.Tasks)
        }
    }

    private fun extractSharedText(intent: Intent): String? {
        val values = buildList {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
            intent.clipData?.let { clip ->
                repeat(clip.itemCount) { index ->
                    clip.getItemAt(index)
                        .coerceToText(this@MainActivity)
                        ?.toString()
                        ?.takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }
        }.distinct()
        return values.takeIf(List<String>::isNotEmpty)?.joinToString("\n")
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this@MainActivity, "剪贴板里没有文字", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.acceptSharedText(text)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleEvent(event: MainViewModel.UiEvent) {
        when (event) {
            is MainViewModel.UiEvent.Message ->
                Toast.makeText(this@MainActivity, event.text, Toast.LENGTH_LONG).show()
            is MainViewModel.UiEvent.OpenMedia -> openMedia(event.location)
            is MainViewModel.UiEvent.ShareMedia -> shareMedia(event.locations)
            is MainViewModel.UiEvent.ChooseOutputDirectory -> directoryPicker.launch(event.currentUri?.let(Uri::parse))
            is MainViewModel.UiEvent.CopyText -> {
                val label = getString(R.string.diagnostic_clip_label, getString(R.string.app_name))
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, event.text))
                Toast.makeText(this@MainActivity, "诊断信息已复制", Toast.LENGTH_SHORT).show()
            }
            is MainViewModel.UiEvent.OpenUrl -> runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
            }.onFailure { Toast.makeText(this@MainActivity, "没有找到可打开网页的应用", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun openMedia(location: String) {
        lifecycleScope.launch {
        val uri = withContext(Dispatchers.IO) { location.toShareableUri() } ?: run {
            Toast.makeText(this@MainActivity, "文件已移动、删除或访问授权失效", Toast.LENGTH_LONG).show()
            return@launch
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolveMimeType(uri, location))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this@MainActivity, "没有找到可打开此文件的应用", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun shareMedia(locations: List<String>) {
        lifecycleScope.launch {
        val items = withContext(Dispatchers.IO) { locations.mapNotNull { location ->
            location.toShareableUri()?.let { uri -> SharedMedia(uri, resolveMimeType(uri, location)) }
        } }
        if (items.size != locations.size || items.isEmpty()) {
            Toast.makeText(this@MainActivity, "部分文件已移动、删除或授权失效，无法完整分享", Toast.LENGTH_LONG).show()
            return@launch
        }
        val intent = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items.single().mimeType
                putExtra(Intent.EXTRA_STREAM, items.single().uri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMimeType(items.map(SharedMedia::mimeType))
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(items.map(SharedMedia::uri)),
                )
            }
        }.apply {
            clipData = ClipData.newUri(contentResolver, getString(R.string.app_name), items.first().uri).also { clip ->
                items.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "分享媒体")) }
            .onFailure { Toast.makeText(this@MainActivity, "暂时无法分享这个文件", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun commonMimeType(types: List<String>): String {
        val distinct = types.distinct()
        if (distinct.size == 1) return distinct.single()
        val groups = distinct.map { it.substringBefore('/') }.distinct()
        return if (groups.size == 1) "${groups.single()}/*" else "*/*"
    }

    private fun String.toShareableUri(): Uri? {
        if (startsWith("content://")) return runCatching {
            val uri = Uri.parse(this)
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { uri }
        }.getOrNull()
        val file = File(this)
        if (!file.isFile) return null
        return runCatching { FileProvider.getUriForFile(
            this@MainActivity,
            "${BuildConfig.APPLICATION_ID}.files",
            file,
        ) }.getOrNull()
    }

    private fun resolveMimeType(uri: Uri, location: String): String =
        contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                location.substringAfterLast('.', "").lowercase(),
            )
            ?: "video/*"

    private data class SharedMedia(val uri: Uri, val mimeType: String)

    companion object {
        const val EXTRA_OPEN_TASKS = "open_tasks"
    }
}
