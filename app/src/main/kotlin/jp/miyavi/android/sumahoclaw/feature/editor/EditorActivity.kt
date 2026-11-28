package jp.miyavi.android.sumahoclaw.feature.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import jp.miyavi.android.sumahoclaw.R
import jp.miyavi.android.sumahoclaw.SumahoClawApp
import jp.miyavi.android.sumahoclaw.core.data.FilesRepository
import jp.miyavi.android.sumahoclaw.core.designsystem.setAppContent
import jp.miyavi.android.sumahoclaw.core.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * エディター（spec 008）。アプリの中から開く（外に出さない）。ほかのアプリからは [ExternalEditorActivity] で受ける。
 * 中身の受け渡しは Intent の短い文字列と、プロセスの中の受け渡し口だけ（research R3）。
 */
open class EditorActivity : ComponentActivity() {

    /** ほかのアプリから来たか（アプリの中の extras を読まない。plan 憲章 IV）。 */
    protected open val fromOutside: Boolean = false

    private val container get() = (application as SumahoClawApp).container
    private var launch: EditorLaunch? = null

    private val viewModel: EditorViewModel by viewModels {
        val launch = requireNotNull(launch)
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditorViewModel(
                    target = launch.target,
                    source = sourceFor(launch),
                    drafts = container.database.fileDrafts(),
                    prefs = SettingsPrefs(container.settings),
                    results = container.editorResults,
                    writeAs = { uri, text, format -> writeTo(contentResolver, uri, text, format) },
                ) as T
        }
    }

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(viewModel::saveAs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        launch = EditorIntents.read(intent, fromOutside)?.let { l ->
            val target = l.target
            if (target is EditTarget.LocalDocument) {
                // 渡された許可を残せるなら残す（プロセスが終わっても下書きから戻せるように。research R8）
                if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                    runCatching { contentResolver.takePersistableUriPermission(target.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    if (intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                        runCatching { contentResolver.takePersistableUriPermission(target.uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    }
                }
                l.copy(target = target.copy(name = displayName(contentResolver, target.uri) ?: target.name))
            } else l
        }
        val launch = launch ?: return finish()

        // 同じ対象を別の画面で開いていたら、そちらに下書きを書かせて閉じる（FR-007）
        container.openEditors.register(launch.target.draftKey, this) { viewModel.closeKeepingDraft() }
            ?.let { closePrevious -> lifecycleScope.launch { closePrevious() } }

        var firstThemeDrawn = false
        val root = findViewById<View>(android.R.id.content)
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!firstThemeDrawn) return false
                root.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        val handle = EditorHandle()
        val target = launch.target
        setAppContent(container.settings, onThemeDrawn = { firstThemeDrawn = true }) {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val content by viewModel.content.collectAsStateWithLifecycle()
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    when (event) {
                        is EditorEvent.Message -> launch {
                            val result = snackbar.showSnackbar(
                                getString(event.text, *event.args.toTypedArray()),
                                actionLabel = if (event.retry) getString(R.string.editor_retry) else null,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.save()
                        }
                        is EditorEvent.TooLarge -> launch {
                            snackbar.showSnackbar(getString(R.string.editor_too_large, Formatter.formatShortFileSize(this@EditorActivity, event.limitBytes)))
                        }
                        is EditorEvent.MoveTo -> handle.moveTo(event.offset)
                        is EditorEvent.CopyAll -> {
                            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(target.name, event.text))
                            launch { snackbar.showSnackbar(getString(R.string.editor_copied)) }
                        }
                        is EditorEvent.SaveAs -> runCatching { createDocument.launch(event.name) }
                        EditorEvent.Close -> finish()
                    }
                }
            }
            EditorContent(
                state = state,
                content = content,
                handle = handle,
                initialLine = launch.line,
                snackbar = snackbar,
                actions = remember(handle) {
                    EditorActions(
                        onBack = { if (viewModel.requestClose()) finish() },
                        onSave = { viewModel.save() },
                        onSaveAs = { runCatching { createDocument.launch(target.name) } },
                        onChanged = viewModel::onChanged,
                        onSelection = viewModel::onSelection,
                        onToggleWrap = viewModel::toggleWrap,
                        onSelectLanguage = viewModel::setLanguage,
                        onSelectCharset = { viewModel.selectCharset(it) },
                        onSearching = viewModel::setSearching,
                        onCopyAll = viewModel::copyAll,
                        onDownload = (target as? EditTarget.GatewayFile)?.let { t -> { container.downloads.start(t.root, t.path) } },
                        onSaveAndClose = viewModel::saveAndClose,
                        onDiscardAndClose = viewModel::discardAndClose,
                        onDismissDialog = viewModel::dismissDialog,
                        onOverwrite = viewModel::overwrite,
                        onReloadDiscarding = viewModel::reloadDiscarding,
                        onAlignAndSave = { viewModel.save(alignSeparators = true) },
                        onConfirmReload = { viewModel.selectCharset(it, confirmed = true) },
                    )
                },
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (launch != null) viewModel.flushDraft()
    }

    override fun onDestroy() {
        launch?.let { container.openEditors.unregister(it.target.draftKey, this) }
        super.onDestroy()
    }

    private fun sourceFor(launch: EditorLaunch): DocumentSource {
        val handoff = container.editorHandoff.take(launch.handoff)
        return when (val t = launch.target) {
            is EditTarget.GatewayFile -> GatewayDocument(FilesRepository(container.gateway), t, (handoff as? HandoffContent.Gateway)?.file)
            is EditTarget.PastedText -> PastedTextDocument(t, (handoff as? HandoffContent.Text)?.text, container.editorResults)
            is EditTarget.LocalDocument -> LocalDocumentSource(t, contentResolver, LocalDocumentSource.grantedWrite(intent))
        }
    }
}

/** ほかのアプリの「アプリで開く」「編集」の受け口（spec 008 US4）。`content://` の文字のファイルだけを受ける。 */
class ExternalEditorActivity : EditorActivity() {
    override val fromOutside: Boolean = true
}

/** `SettingsStore` をエディターの好みの口にする。 */
private class SettingsPrefs(private val settings: SettingsStore) : EditorPrefs {
    override fun wrap(prose: Boolean): Flow<Boolean> = settings.editorWrap(prose)
    override suspend fun setWrap(prose: Boolean, wrap: Boolean) = settings.setEditorWrap(prose, wrap).let { }
    override val languages: Flow<Map<String, String>> get() = settings.editorLanguages
    override suspend fun setLanguage(key: String, language: String?) = settings.setEditorLanguage(key, language).let { }
}

/** アプリの中からエディターを開く。読み込み済みの中身があれば受け渡し口に置く（Intent に本文を載せない）。 */
fun android.content.Context.openEditor(target: EditTarget, content: HandoffContent? = null, line: Int = 0) {
    val container = (applicationContext as SumahoClawApp).container
    val handoff = content?.let(container.editorHandoff::put)
    startActivity(EditorIntents.intent(this, EditorLaunch(target, handoff, line)))
}
