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

/** 外観。テーマとは独立（data-model 4）。 */
enum class Appearance { SYSTEM, LIGHT, DARK }

/** ベーステーマ（FR-081）。 */
enum class AppTheme { OPENCLAW, MODERN, DYNAMIC, CUSTOM }

/** 浮いている面の透かし方（FR-082）。 */
enum class SurfaceStyle { GLASS, BLUR }

enum class AppFont { LINE_SEED_JP, SYSTEM }

enum class LockScreenContent { FOLLOW_SYSTEM, HIDE }

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
    val backgroundConnection: Boolean = true,
    val notifyReplies: Boolean = true,
    val notifyApprovals: Boolean = true,
    val notifyAutomationFailures: Boolean = true,
    val lockScreenContent: LockScreenContent = LockScreenContent.FOLLOW_SYSTEM,
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
            backgroundConnection = p[BACKGROUND_CONNECTION] ?: true,
            notifyReplies = p[NOTIFY_REPLIES] ?: true,
            notifyApprovals = p[NOTIFY_APPROVALS] ?: true,
            notifyAutomationFailures = p[NOTIFY_AUTOMATION_FAILURES] ?: true,
            lockScreenContent = p[LOCK_SCREEN].toEnum(LockScreenContent.FOLLOW_SYSTEM),
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
    suspend fun setLockScreenContent(v: LockScreenContent) = store.edit { it[LOCK_SCREEN] = v.name }

    suspend fun selectAgent(agentId: String) = store.edit { it[SELECTED_AGENT] = agentId }
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
        val LOCK_SCREEN = stringPreferencesKey("lock_screen_content")
        val SELECTED_AGENT = stringPreferencesKey("selected_agent")
        val SELECTED_SESSIONS = stringPreferencesKey("selected_sessions")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
