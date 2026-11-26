package jp.miyavi.android.clawdroid

import android.app.Application
import android.os.Build
import jp.miyavi.android.clawdroid.core.data.ChatRepository
import jp.miyavi.android.clawdroid.core.data.GatewayControlRepository
import jp.miyavi.android.clawdroid.core.data.SessionsRepository
import jp.miyavi.android.clawdroid.core.database.ClawdroidDatabase
import jp.miyavi.android.clawdroid.core.gateway.ClientInfo
import jp.miyavi.android.clawdroid.core.gateway.DeviceIdentityRepository
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.security.SecretStore
import jp.miyavi.android.clawdroid.core.security.TinkSecretStore
import jp.miyavi.android.clawdroid.core.settings.SettingsStore
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

    val database = ClawdroidDatabase.create(app)
    val sessions = SessionsRepository(gateway, appScope)
    val chats = ChatRepository(gateway, appScope)
    val inboundImages = jp.miyavi.android.clawdroid.core.data.InboundImageLoader(gateway)
    val gatewayControl = GatewayControlRepository(gateway)
    internal val approvals = jp.miyavi.android.clawdroid.core.data.ApprovalsRepository(gateway, appScope)
    internal val questions = jp.miyavi.android.clawdroid.core.data.QuestionsRepository(gateway, appScope)

    /** アプリが前にあるか・開いている会話・通知から開く行き先（spec 006）。 */
    val foreground = jp.miyavi.android.clawdroid.core.notify.ForegroundState()
    /** エージェントの顔の画像（通知・ショートカットのアイコン）。 */
    val avatarBitmaps = jp.miyavi.android.clawdroid.core.data.AgentAvatarBitmaps(app)
    internal val notifier = jp.miyavi.android.clawdroid.core.notify.Notifier(app, gateway, sessions, approvals, settings, foreground, appScope, avatarBitmaps)

    /** ランチャーのショートカット（spec 007）。 */
    val shortcuts = jp.miyavi.android.clawdroid.core.shortcut.ShortcutPublisher(app, settings, sessions, gateway, secrets, foreground, avatarBitmaps, appScope)

    /** アプリ全体のダウンロード（ファイルビューアを閉じても、画面を消しても続く）。 */
    val downloads = jp.miyavi.android.clawdroid.core.data.DownloadCenter(
        context = app,
        scope = appScope,
        downloader = jp.miyavi.android.clawdroid.core.data.FileDownloader(
            jp.miyavi.android.clawdroid.core.data.FilesRepository(gateway), gateway, app.contentResolver,
        ),
    )
}

class ClawdroidApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.gateway.start()
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
