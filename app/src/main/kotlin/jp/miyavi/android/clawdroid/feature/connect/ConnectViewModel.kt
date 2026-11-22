package jp.miyavi.android.clawdroid.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.GatewayUrl
import jp.miyavi.android.clawdroid.core.gateway.UrlCheck
import jp.miyavi.android.clawdroid.core.security.SecretStore
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
    val submitted: Boolean = false,
)

data class ConnectUiState(val form: ConnectForm, val connection: ConnectionState)

/** 接続画面（US1、FR-001・004）。入力は失敗しても残す。 */
class ConnectViewModel(
    private val gateway: GatewayClient,
    private val secrets: SecretStore,
) : ViewModel() {
    private val form = MutableStateFlow(ConnectForm())

    val state: StateFlow<ConnectUiState> = combine(form, gateway.state) { f, c -> ConnectUiState(f, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectUiState(ConnectForm(), gateway.state.value))

    init {
        // 以前の接続先を入れておく（トークンは表示しない）
        viewModelScope.launch {
            secrets.loadConnection()?.let { saved -> form.update { it.copy(url = saved.url) } }
        }
    }

    fun onUrlChange(v: String) = form.update { it.copy(url = v, urlError = null) }
    fun onTokenChange(v: String) = form.update { it.copy(token = v, tokenError = null) }
    fun toggleTokenVisible() = form.update { it.copy(tokenVisible = !it.tokenVisible) }

    fun submit() {
        val current = form.value
        val urlCheck = GatewayUrl.check(current.url)
        val urlError = when (urlCheck) {
            UrlCheck.Invalid -> FieldError.URL_INVALID
            UrlCheck.CleartextPublic -> FieldError.CLEARTEXT_PUBLIC
            is UrlCheck.Ok -> null
        }
        val tokenError = if (current.token.isBlank()) FieldError.TOKEN_EMPTY else null
        form.update { it.copy(urlError = urlError, tokenError = tokenError, submitted = urlError == null && tokenError == null) }
        if (urlCheck !is UrlCheck.Ok || tokenError != null) return
        viewModelScope.launch { gateway.connect(urlCheck.normalized, current.token.trim()) }
    }
}
