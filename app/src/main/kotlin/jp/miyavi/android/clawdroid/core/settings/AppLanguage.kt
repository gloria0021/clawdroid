package jp.miyavi.android.clawdroid.core.settings

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * アプリ単位の言語（Android 13 の LocaleManager）。端末の設定の「アプリの言語」と同じ値を読み書きする。
 * 選べる言語は res/xml/locales_config.xml（Debug は日本語だけ、Release は訳した言語すべて）。
 */
object AppLanguage {
    /** 選べる言語の BCP 47 タグ（locales_config.xml の並び）。 */
    fun supported(context: Context): List<String> {
        val locales = runCatching { LocaleConfig(context).supportedLocales }.getOrNull() ?: return emptyList()
        return (0 until locales.size()).map { locales[it].toLanguageTag() }
    }

    /** 選んでいる言語のタグ。端末の言語に従っているなら null。 */
    fun current(context: Context): String? {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
        return if (locales.isEmpty) null else locales[0].toLanguageTag()
    }

    /** 言語を切り替える（null なら端末の言語に戻す）。画面は端末が作り直す。 */
    fun set(context: Context, tag: String?) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    /** 言語の名前を、その言語自身で書いたもの（例: English、日本語、Deutsch）。 */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
