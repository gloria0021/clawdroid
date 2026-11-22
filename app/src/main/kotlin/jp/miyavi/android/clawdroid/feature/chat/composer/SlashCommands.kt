package jp.miyavi.android.clawdroid.feature.chat.composer

import jp.miyavi.android.clawdroid.core.data.SettingChoice
import jp.miyavi.android.clawdroid.core.data.SlashCommand

/**
 * 入力欄の上に出す補完の候補（`/` のコマンドと `$` のスキル）。
 * 押すと、入力の [start]〜[end] を [replacement] に置き換える。
 */
sealed interface SlashSuggestion {
    /** LazyColumn の key。 */
    val key: String
    val replacement: String
    val start: Int
    val end: Int

    data class Command(val command: SlashCommand, override val end: Int) : SlashSuggestion {
        override val key get() = "command:${command.name}"
        override val start get() = 0
        // 引数を取るコマンドは、続けて引数を打てるように空白まで入れる
        override val replacement get() = "/${command.name}" + if (command.acceptsArgs) " " else ""
    }

    data class Choice(val command: SlashCommand, val choice: SettingChoice, override val end: Int) : SlashSuggestion {
        override val key get() = "choice:${command.name}:${choice.id}"
        override val start get() = 0
        override val replacement get() = "/${command.name} ${choice.id}"
    }

    data class Skill(val command: SlashCommand, override val start: Int, override val end: Int, val atEnd: Boolean) : SlashSuggestion {
        override val key get() = "skill:${command.name}"
        // 文の終わりなら、続けて打てるように空白を足す（Control UI と同じ）
        override val replacement get() = "$${command.name}" + if (atEnd) " " else ""
    }
}

/** 候補で置き換えた文字と、置き換えたあとのカーソルの位置。 */
internal fun SlashSuggestion.applyTo(text: String): Pair<String, Int> {
    val start = start.coerceIn(0, text.length)
    val end = end.coerceIn(start, text.length)
    return text.substring(0, start) + replacement + text.substring(end) to start + replacement.length
}

/**
 * 入力中の文字とカーソルの位置から候補を作る。
 * - `$`: カーソルの前の `$` で始まる語をスキルで補完する（文のどこでも。Control UI と同じ）
 * - `/` で始まり、まだ空白が無い: コマンド名の候補（名前・別名の前方一致。無ければ途中一致。スキルのコマンドは最後）
 * - `/name ` の後ろ: そのコマンドの最初の引数の選択肢（前方一致）
 * 打ち終えたものと完全に一致する候補しか無いときは、何も出さない。
 */
internal fun slashSuggestions(text: String, commands: List<SlashCommand>, cursor: Int = text.length): List<SlashSuggestion> {
    if (commands.isEmpty()) return emptyList()
    skillSuggestions(text, commands, cursor)?.let { return it }
    if (!text.startsWith("/") || '\n' in text) return emptyList()
    val body = text.substring(1)
    val space = body.indexOf(' ')
    if (space < 0) {
        val query = body.lowercase()
        // 打った文字で始まるものだけに絞る（/re → /reset など）。始まるものが無いときだけ、途中に含むものを出す
        val prefix = commands.filter { command -> command.allNames.any { it.lowercase().startsWith(query) } }
        val candidates = prefix.ifEmpty { commands.filter { command -> command.allNames.any { query in it.lowercase() } } }
        val (skills, others) = candidates.partition(SlashCommand::isSkill)
        val matched = (others + skills).take(MAX_SUGGESTIONS)
        val done = matched.singleOrNull()?.takeIf { command -> command.allNames.any { it.lowercase() == query } }
        return if (done != null && !done.acceptsArgs) emptyList() else matched.map { SlashSuggestion.Command(it, text.length) }
    }
    val name = body.substring(0, space).lowercase()
    val command = commands.firstOrNull { c -> c.allNames.any { it.lowercase() == name } } ?: return emptyList()
    val argument = body.substring(space + 1).trimStart()
    // 補完するのは最初の引数だけ
    if (' ' in argument.trimEnd()) return emptyList()
    val query = argument.trimEnd().lowercase()
    val choices = command.args.firstOrNull()?.choices.orEmpty()
        .filter { it.id.lowercase().startsWith(query) || it.label.lowercase().startsWith(query) }
        .take(MAX_SUGGESTIONS)
    if (choices.singleOrNull()?.id?.lowercase() == query) return emptyList()
    return choices.map { SlashSuggestion.Choice(command, it, text.length) }
}

/**
 * カーソルが `$` で始まる語の中にあれば、スキルの候補（無ければ空の一覧）。`$` の語でなければ null。
 * `$` は文の先頭か空白の後ろだけ。小文字を含まない語（`$5` など）は値段とみなして補完しない。
 */
private fun skillSuggestions(text: String, commands: List<SlashCommand>, cursor: Int): List<SlashSuggestion>? {
    val at = cursor.coerceIn(0, text.length)
    var tokenStart = at
    while (tokenStart > 0 && text[tokenStart - 1].isSkillChar()) tokenStart--
    val dollar = tokenStart - 1
    if (dollar < 0 || text[dollar] != '$') return null
    if (dollar > 0 && !text[dollar - 1].isWhitespace()) return null
    var end = at
    while (end < text.length && text[end].isSkillChar()) end++
    val query = text.substring(tokenStart, end).trimEnd(':')
    if (query.isNotEmpty() && query.none { it in 'a'..'z' }) return null
    val normalized = query.lowercase().replace(SKILL_SEPARATORS, "-")
    val skills = commands.filter { it.isSkill && it.skillModelVisible }.filter { command ->
        val display = command.skillName.orEmpty().lowercase()
        query.isEmpty() || query.lowercase() in display ||
            normalized in display.replace(SKILL_SEPARATORS, "-") ||
            command.name.lowercase().replace(SKILL_SEPARATORS, "-").startsWith(normalized)
    }.sortedBy { it.skillName.orEmpty().lowercase() }
    if (skills.singleOrNull()?.name?.lowercase() == query.lowercase()) return emptyList()
    return skills.take(MAX_SUGGESTIONS).map { SlashSuggestion.Skill(it, dollar, end, atEnd = end == text.length) }
}

private fun Char.isSkillChar() = isLetterOrDigit() || this == '_' || this == '-' || this == ':'

private val SKILL_SEPARATORS = Regex("[\\s_]+")

/** 一度に出す候補の上限。 */
private const val MAX_SUGGESTIONS = 50
