package jp.miyavi.android.sumahoclaw.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.sumahoclaw.core.gateway.ConnectionState
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient
import jp.miyavi.android.sumahoclaw.core.gateway.GatewayUrl
import jp.miyavi.android.sumahoclaw.core.gateway.UrlCheck
import jp.miyavi.android.sumahoclaw.core.security.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FieldError { URL_INVALID, CLEARTEXT_PUBLIC, TOKEN_EMPTY }

data class ConnectForm(
    val url: String = "",
    val token: String = "",
    val tokenVisible: Boolean = false,
    val urlError: FieldError? = null,
    val tokenError: FieldError? = null,
    /** 保存済みのトークンがある（空欄のまま接続すると、それを使う）。 */
    val hasSavedToken: Boolean = false,
    /** 接続先のホスト（状態の画面の説明と、Tailscale の案内に使う）。 */
    val host: String? = null,
)

data class ConnectUiState(val form: ConnectForm, val connection: ConnectionState) {
    val tailscale: Boolean get() = form.host?.let(GatewayUrl::isTailscaleHost) == true
}

/** 接続画面（US1、FR-001・004）。入力は失敗しても残す。 */
class ConnectViewModel(
    private val gateway: GatewayClient,
    private val secrets: SecretStore,
) : ViewModel() {
    private val form = MutableStateFlow(ConnectForm())
    private var savedToken: String? = null

    val state: StateFlow<ConnectUiState> = combine(form, gateway.state) { f, c -> ConnectUiState(f, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectUiState(ConnectForm(), gateway.state.value))

    init {
        // 以前の接続先を入れておく（トークンは表示せず、空欄なら保存済みのものを使う）
        viewModelScope.launch {
            secrets.loadConnection()?.let { saved ->
                savedToken = saved.gatewayToken.takeIf { it.isNotBlank() }
                form.update { it.copy(url = saved.url, hasSavedToken = savedToken != null, host = GatewayUrl.hostOf(saved.url)) }
            }
        }
    }

    fun onUrlChange(v: String) = form.update { it.copy(url = v, urlError = null) }
    fun onTokenChange(v: String) = form.update { it.copy(token = v, tokenError = null) }
    fun toggleTokenVisible() = form.update { it.copy(tokenVisible = !it.tokenVisible) }

    /** 状態の画面から入力に戻る（接続の試行は止める）。 */
    fun editConnection() = gateway.disconnect()

    /** 待ち時間を飛ばして、すぐに接続し直す。 */
    fun retry() = gateway.reconnectNow()

    fun submit() {
        val current = form.value
        val urlCheck = GatewayUrl.check(current.url)
        val urlError = when (urlCheck) {
            UrlCheck.Invalid -> FieldError.URL_INVALID
            UrlCheck.CleartextPublic -> FieldError.CLEARTEXT_PUBLIC
            is UrlCheck.Ok -> null
        }
        val token = current.token.trim().ifEmpty { savedToken.orEmpty() }
        val tokenError = if (token.isBlank()) FieldError.TOKEN_EMPTY else null
        form.update { it.copy(urlError = urlError, tokenError = tokenError) }
        if (urlCheck !is UrlCheck.Ok || tokenError != null) return
        form.update { it.copy(host = GatewayUrl.hostOf(urlCheck.normalized)) }
        viewModelScope.launch { gateway.connect(urlCheck.normalized, token) }
    }
}
