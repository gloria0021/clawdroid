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
    }
}
