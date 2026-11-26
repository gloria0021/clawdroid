package jp.miyavi.android.clawdroid.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import jp.miyavi.android.clawdroid.core.shortcut.ShortcutSettings

/** 外観。テーマとは独立（data-model 4）。 */
enum class Appearance { SYSTEM, LIGHT, DARK }

/** ベーステーマ（FR-081）。 */
enum class AppTheme { OPENCLAW, MODERN, DYNAMIC, CUSTOM }

/** 浮いている面の透かし方（FR-082）。 */
enum class SurfaceStyle { GLASS, BLUR }

enum class AppFont { LINE_SEED_JP, SYSTEM }

/** セッション履歴の並べ方。 */
enum class SessionGrouping { UPDATED, FOLDER }

data class ThemeSettings(
    val appearance: Appearance = Appearance.SYSTEM,
    val theme: AppTheme = AppTheme.OPENCLAW,
    val customSeedArgb: Int? = null,
    val surfaceStyle: SurfaceStyle = SurfaceStyle.GLASS,
    /** ガラスの屈折の強さ（0〜10）。 */
    val glassRefraction: Int = DEFAULT_GLASS_REFRACTION,
    /** ぼかしの強さ（0〜10）。 */
    val blurStrength: Int = DEFAULT_BLUR_STRENGTH,
    val font: AppFont = AppFont.LINE_SEED_JP,
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
) {
    companion object {
        const val MIN_FONT_SIZE_SP = 14
        const val MAX_FONT_SIZE_SP = 20
        const val DEFAULT_FONT_SIZE_SP = 16
        const val MIN_LEVEL = 0
        const val MAX_LEVEL = 10
        const val DEFAULT_GLASS_REFRACTION = 6
        const val DEFAULT_BLUR_STRENGTH = 3
    }
}

data class NotificationSettings(
    /** 通知ぜんたいのオン・オフ（オフなら常駐もしない。マスター指定 2026-11-24）。 */
    val enabled: Boolean = true,
    /** 常時接続（常駐して裏でも通知を受ける）。電池を使うので既定はオフ（マスター指定 2026-11-26）。 */
    val backgroundConnection: Boolean = false,
    val notifyReplies: Boolean = true,
    val notifyApprovals: Boolean = true,
    val notifyAutomationFailures: Boolean = true,
)

data class UiSelection(
    val selectedAgentId: String? = null,
    val selectedSessionKeyByAgent: Map<String, String> = emptyMap(),
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 端末の設定（アプリが正本）。秘密情報は置かない（それは SecretStore）。 */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val theme: Flow<ThemeSettings> = store.data.map { p ->
        ThemeSettings(
            appearance = p[APPEARANCE].toEnum(Appearance.SYSTEM),
            theme = p[THEME].toEnum(AppTheme.OPENCLAW),
            customSeedArgb = p[CUSTOM_SEED],
            font = p[FONT].toEnum(AppFont.LINE_SEED_JP),
            fontSizeSp = (p[FONT_SIZE] ?: ThemeSettings.DEFAULT_FONT_SIZE_SP).coerceIn(ThemeSettings.MIN_FONT_SIZE_SP, ThemeSettings.MAX_FONT_SIZE_SP),
            surfaceStyle = p[SURFACE_STYLE].toEnum(SurfaceStyle.GLASS),
            glassRefraction = (p[GLASS_REFRACTION] ?: ThemeSettings.DEFAULT_GLASS_REFRACTION).coerceIn(ThemeSettings.MIN_LEVEL, ThemeSettings.MAX_LEVEL),
            blurStrength = (p[BLUR_STRENGTH] ?: ThemeSettings.DEFAULT_BLUR_STRENGTH).coerceIn(ThemeSettings.MIN_LEVEL, ThemeSettings.MAX_LEVEL),
        )
    }.distinctUntilChanged()

    val notifications: Flow<NotificationSettings> = store.data.map { p ->
        NotificationSettings(
            enabled = p[NOTIFICATIONS_ENABLED] ?: true,
            backgroundConnection = p[BACKGROUND_CONNECTION] ?: false,
            notifyReplies = p[NOTIFY_REPLIES] ?: true,
            notifyApprovals = p[NOTIFY_APPROVALS] ?: true,
            notifyAutomationFailures = p[NOTIFY_AUTOMATION_FAILURES] ?: true,
        )
    }.distinctUntilChanged()

    val selection: Flow<UiSelection> = store.data.map { p ->
        UiSelection(
            selectedAgentId = p[SELECTED_AGENT],
            selectedSessionKeyByAgent = p[SELECTED_SESSIONS]?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
            } ?: emptyMap(),
        )
    }.distinctUntilChanged()

    suspend fun setAppearance(v: Appearance) = store.edit { it[APPEARANCE] = v.name }
    suspend fun setFont(v: AppFont) = store.edit { it[FONT] = v.name }
    suspend fun setFontSize(v: Int) = store.edit {
        it[FONT_SIZE] = v.coerceIn(ThemeSettings.MIN_FONT_SIZE_SP, ThemeSettings.MAX_FONT_SIZE_SP)
    }
    suspend fun setTheme(v: AppTheme) = store.edit { it[THEME] = v.name }
    suspend fun setCustomSeed(argb: Int) = store.edit {
        it[CUSTOM_SEED] = argb
        it[THEME] = AppTheme.CUSTOM.name
    }
    suspend fun setSurfaceStyle(v: SurfaceStyle) = store.edit { it[SURFACE_STYLE] = v.name }
    suspend fun setGlassRefraction(v: Int) = store.edit { it[GLASS_REFRACTION] = v.coerceIn(ThemeSettings.MIN_LEVEL, ThemeSettings.MAX_LEVEL) }
    suspend fun setBlurStrength(v: Int) = store.edit { it[BLUR_STRENGTH] = v.coerceIn(ThemeSettings.MIN_LEVEL, ThemeSettings.MAX_LEVEL) }

    suspend fun setBackgroundConnection(v: Boolean) = store.edit { it[BACKGROUND_CONNECTION] = v }
    suspend fun setNotifyReplies(v: Boolean) = store.edit { it[NOTIFY_REPLIES] = v }
    suspend fun setNotifyApprovals(v: Boolean) = store.edit { it[NOTIFY_APPROVALS] = v }
    suspend fun setNotifyAutomationFailures(v: Boolean) = store.edit { it[NOTIFY_AUTOMATION_FAILURES] = v }

    suspend fun setNotifications(v: NotificationSettings) = store.edit {
        it[NOTIFICATIONS_ENABLED] = v.enabled
        it[BACKGROUND_CONNECTION] = v.backgroundConnection
        it[NOTIFY_REPLIES] = v.notifyReplies
        it[NOTIFY_APPROVALS] = v.notifyApprovals
        it[NOTIFY_AUTOMATION_FAILURES] = v.notifyAutomationFailures
    }

    suspend fun selectAgent(agentId: String) = store.edit { it[SELECTED_AGENT] = agentId }
    /** サイドパネルに出すエージェントの並びと表示（設定 → エージェント）。 */
    val agentLayout: Flow<AgentLayout> = store.data.map { p ->
        p[AGENT_LAYOUT]?.let { runCatching { json.decodeFromString<AgentLayout>(it) }.getOrNull() } ?: AgentLayout()
    }.distinctUntilChanged()

    /** セッション履歴の並べ方（エージェントごと。マスター指定 2026-11-23）。無ければ更新順。 */
    val sessionGrouping: Flow<Map<String, SessionGrouping>> = store.data.map { p ->
        p[SESSION_GROUPING_BY_AGENT]?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()
            .mapNotNull { (agent, v) -> runCatching { SessionGrouping.valueOf(v) }.getOrNull()?.let { agent to it } }.toMap()
    }.distinctUntilChanged()

    suspend fun setSessionGrouping(agentId: String, v: SessionGrouping) = store.edit { p ->
        val current = p[SESSION_GROUPING_BY_AGENT]?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()
        p[SESSION_GROUPING_BY_AGENT] = json.encodeToString(current + (agentId to v.name))
    }

    /** コードを折り返すか（spec 005 FR-053。既定は折り返さない）。 */
    val codeWrap: Flow<Boolean> = store.data.map { it[CODE_WRAP] ?: false }.distinctUntilChanged()

    suspend fun setCodeWrap(v: Boolean) = store.edit { it[CODE_WRAP] = v }

    suspend fun setAgentLayout(layout: AgentLayout) = store.edit { it[AGENT_LAYOUT] = json.encodeToString(layout) }

    /** ランチャーのショートカットの枠（spec 007 R7）。読めなければ空。 */
    val shortcuts: Flow<ShortcutSettings> = store.data.map { p ->
        p[SHORTCUTS]?.let { runCatching { json.decodeFromString<ShortcutSettings>(it) }.getOrNull() } ?: ShortcutSettings()
    }.distinctUntilChanged()

    /** 枠を書き換える（読んで書くあいだに別の書き込みが割り込まないよう、DataStore の 1 回の編集で行う）。 */
    suspend fun updateShortcuts(transform: (ShortcutSettings) -> ShortcutSettings) = store.edit { p ->
        val current = p[SHORTCUTS]?.let { runCatching { json.decodeFromString<ShortcutSettings>(it) }.getOrNull() } ?: ShortcutSettings()
        val next = transform(current)
        if (next != current) p[SHORTCUTS] = json.encodeToString(next)
    }

    suspend fun selectSession(agentId: String, sessionKey: String?) = store.edit { p ->
        val current = p[SELECTED_SESSIONS]?.let {
            runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
        } ?: emptyMap()
        val next = if (sessionKey == null) current - agentId else current + (agentId to sessionKey)
        p[SELECTED_SESSIONS] = json.encodeToString(next)
        p[SELECTED_AGENT] = agentId
    }

    private companion object {
        val APPEARANCE = stringPreferencesKey("appearance")
        val THEME = stringPreferencesKey("theme")
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = intPreferencesKey("font_size_sp")
        val CUSTOM_SEED = intPreferencesKey("custom_seed")
        val SURFACE_STYLE = stringPreferencesKey("surface_style")
        val GLASS_REFRACTION = intPreferencesKey("glass_refraction")
        val BLUR_STRENGTH = intPreferencesKey("blur_strength")
        val BACKGROUND_CONNECTION = booleanPreferencesKey("background_connection")
        val NOTIFY_REPLIES = booleanPreferencesKey("notify_replies")
        val NOTIFY_APPROVALS = booleanPreferencesKey("notify_approvals")
        val NOTIFY_AUTOMATION_FAILURES = booleanPreferencesKey("notify_automation_failures")
        val SELECTED_AGENT = stringPreferencesKey("selected_agent")
        val SELECTED_SESSIONS = stringPreferencesKey("selected_sessions")
        val AGENT_LAYOUT = stringPreferencesKey("agent_layout")
        val SESSION_GROUPING_BY_AGENT = stringPreferencesKey("session_grouping_by_agent")
        val CODE_WRAP = booleanPreferencesKey("code_wrap")
        val SHORTCUTS = stringPreferencesKey("shortcuts")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
