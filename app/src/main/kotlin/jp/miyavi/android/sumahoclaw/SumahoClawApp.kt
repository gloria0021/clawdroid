package jp.miyavi.android.sumahoclaw

import android.app.Application
import android.os.Build
import jp.miyavi.android.sumahoclaw.core.data.ChatRepository
import jp.miyavi.android.sumahoclaw.core.data.GatewayControlRepository
import jp.miyavi.android.sumahoclaw.core.data.SessionsRepository
import jp.miyavi.android.sumahoclaw.core.database.SumahoClawDatabase
import jp.miyavi.android.sumahoclaw.core.gateway.ClientInfo
import jp.miyavi.android.sumahoclaw.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.security.SecretStore
import jp.miyavi.android.sumahoclaw.core.security.TinkSecretStore
import jp.miyavi.android.sumahoclaw.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** アプリ全体で 1 つずつ持つもの（手書きの依存の受け渡し）。 */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val secrets: SecretStore = TinkSecretStore(app)
    val identities = DeviceIdentityRepository(secrets)

    /** アプリ側から ping は送らない（Gateway の ping に pong を返すだけ。research R6）。 */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val gateway = GatewayClient(
        scope = appScope,
        httpClient = httpClient,
        identities = identities,
        secrets = secrets,
        clientInfo = ClientInfo(
            version = BuildConfig.VERSION_NAME,
            modelIdentifier = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim().ifEmpty { null },
            displayName = Build.MODEL,
        ),
    )

    val database = SumahoClawDatabase.create(app)
    val sessions = SessionsRepository(
        gateway,
        appScope,
        jp.miyavi.android.sumahoclaw.core.data.AgentRosterCache(java.io.File(app.filesDir, "agent-roster")),
    )
    val chats = ChatRepository(gateway, appScope)
    val claudeUsage = jp.miyavi.android.sumahoclaw.core.data.ClaudeCliUsageRepository(
        jp.miyavi.android.sumahoclaw.core.data.TerminalRepository(
            gateway,
            jp.miyavi.android.sumahoclaw.core.data.SharedPreferencesTerminalIdStore(
                app.getSharedPreferences("terminal_ids", Application.MODE_PRIVATE),
            ),
        ),
        secrets,
    )
    val inboundImages = jp.miyavi.android.sumahoclaw.core.data.InboundImageLoader(gateway)
    /** 会話のリンクのタイトルとファビコン（端末から読む）。 */
    val linkPreviews = jp.miyavi.android.sumahoclaw.core.data.LinkPreviewLoader(httpClient)
    /** 会話の添付ファイルを保存した先（「開く」を出し続けるため）。 */
    val savedFiles = jp.miyavi.android.sumahoclaw.core.data.SavedFileStore(app.getSharedPreferences("saved_files", Application.MODE_PRIVATE))
    val gatewayControl = GatewayControlRepository(gateway)
    internal val approvals = jp.miyavi.android.sumahoclaw.core.data.ApprovalsRepository(gateway, appScope)
    internal val questions = jp.miyavi.android.sumahoclaw.core.data.QuestionsRepository(gateway, appScope)

    /** アプリが前にあるか・開いている会話・通知から開く行き先（spec 006）。 */
    val foreground = jp.miyavi.android.sumahoclaw.core.notify.ForegroundState()
    /** エージェントの顔の画像（通知・ショートカットのアイコン）。 */
    val avatarBitmaps = jp.miyavi.android.sumahoclaw.core.data.AgentAvatarBitmaps(app)
    internal val notifier = jp.miyavi.android.sumahoclaw.core.notify.Notifier(app, gateway, sessions, approvals, settings, foreground, appScope, avatarBitmaps)

    /** ランチャーのショートカット（spec 007）。 */
    val shortcuts = jp.miyavi.android.sumahoclaw.core.shortcut.ShortcutPublisher(app, settings, sessions, gateway, secrets, foreground, avatarBitmaps, appScope)

    /** アプリ全体のダウンロード（ファイルビューアを閉じても、画面を消しても続く）。 */
    val downloads = jp.miyavi.android.sumahoclaw.core.data.DownloadCenter(
        context = app,
        scope = appScope,
        downloader = jp.miyavi.android.sumahoclaw.core.data.FileDownloader(
            jp.miyavi.android.sumahoclaw.core.data.FilesRepository(gateway), gateway, app.contentResolver,
        ),
    )
}

class SumahoClawApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // つなぐかは Notifier が決める（前にある・常時接続・承認待ちやダウンロード中のときだけ）
        container.gateway.startPaused()
        container.notifier.start(busy = container.downloads.tasks.map { it.isNotEmpty() })
        container.shortcuts.start()
        // 前後の切り替え: 裏では文字ごとの流れを止め（spec 006 FR-004）、前に来たら常駐を始め直す（裏からは始められないため）
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                container.foreground.setForeground(true)
                container.chats.setBackground(false)
                container.notifier.ensureService()
            }

            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                container.foreground.setForeground(false)
                container.chats.setBackground(true)
            }
        })
    }
}
