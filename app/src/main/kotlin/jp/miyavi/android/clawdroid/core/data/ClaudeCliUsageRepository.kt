package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.security.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Claude の使用枠はホストの CLI から取得する。Gateway の使用量 API にはフォールバックしない。 */
class ClaudeCliUsageRepository(
    private val terminal: TerminalRepository,
    private val secrets: SecretStore,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val lock = Mutex()
    private val cache = mutableMapOf<Pair<String, String?>, Pair<Long, ProviderUsage?>>()

    suspend fun load(agentId: String?): ProviderUsage? = lock.withLock {
        val host = secrets.loadConnection()?.url ?: return@withLock null
        val key = host to agentId
        cache[key]?.takeIf { elapsedMillis() - it.first < REFRESH_MS }?.let { return@withLock it.second }
        val nonce = UUID.randomUUID().toString()
        val usage = try {
            terminal.runOnce(agentId, claudeUsageCommand(nonce), 65_000) {
                parseClaudeUsageOutput(it, nonce, System.currentTimeMillis())
            }?.singleOrNull()
        } catch (_: TimeoutCancellationException) {
            null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        // 失敗時も連続で CLI を起動せず、期限切れの使用枠は表示しない。
        cache[key] = elapsedMillis() to usage
        usage
    }

    private companion object {
        const val REFRESH_MS = 5 * 60_000L
    }
}

internal fun isClaudeCliModel(reference: String?): Boolean =
    reference?.substringBefore('/')?.equals("claude-cli", ignoreCase = true) == true

internal fun usesClaudeCli(session: SessionSummary?, agent: Agent?, draftModel: String?): Boolean {
    val model = draftModel ?: session?.modelReference ?: session?.model ?: agent?.defaultModel
    val runtime = session?.runtimeId ?: agent?.runtimeId?.takeIf { model == agent.defaultModel }
    return runtime?.equals("claude-cli", ignoreCase = true) ?: isClaudeCliModel(model)
}

internal fun claudeUsageCommand(nonce: String): String {
    val script = """cd "${'$'}{TMPDIR:-/tmp}" || exit; output=${'$'}(claude -p /usage --no-session-persistence </dev/null 2>&1); code=${'$'}?; printf '\036%s:start\036\n%s\n\036%s:end:%s\036\n' "${'$'}1" "${'$'}output" "${'$'}1" "${'$'}code""""
    return "unset HISTFILE\rstty -echo; sh -c ${shellQuote(script)} clawdroid-claude-usage ${shellQuote(nonce)}\r"
}

/** null は出力待ち、空リストは終了したが取得失敗。エコーされたコマンドは印として扱わない。 */
internal fun parseClaudeUsageOutput(output: String, nonce: String, now: Long): List<ProviderUsage>? {
    val start = "\u001e$nonce:start\u001e"
    val from = output.indexOf(start).takeIf { it >= 0 } ?: return null
    val end = Regex("\u001e${Regex.escape(nonce)}:end:(\\d+)\u001e").find(output, from + start.length) ?: return null
    if (end.groupValues[1] != "0") return emptyList()
    val text = output.substring(from + start.length, end.range.first).replace(CLAUDE_USAGE_ANSI, "").replace("\r", "")
    return parseClaudeUsage(text, now)?.let(::listOf) ?: emptyList()
}

internal fun parseClaudeUsage(text: String, now: Long): ProviderUsage? = runCatching {
    val windows = CLAUDE_USAGE_WINDOWS.map { (label, pattern) ->
        val match = pattern.find(text) ?: return null
        val used = match.groupValues[1].toInt().takeIf { it in 0..100 } ?: return null
        val resetAt = claudeUsageReset(match.groupValues[2], now) ?: return null
        UsageWindow(label, used / 100f, resetAt)
    }
    ProviderUsage("Claude", windows, ids = listOf("claude-cli", "anthropic"))
}.getOrNull()

private fun claudeUsageReset(text: String, now: Long): Long? {
    val match = CLAUDE_USAGE_RESET.matchEntire(text.trim()) ?: return null
    val (month, day, hour, minute, ampm, zone) = match.destructured
    val h = hour.toInt().takeIf { it in 1..12 } ?: return null
    val m = minute.ifEmpty { "0" }.toInt().takeIf { it in 0..59 } ?: return null
    val tz = ZoneId.of(zone)
    val local = Instant.ofEpochMilli(now).atZone(tz)
    fun at(date: LocalDate) = date.atTime(h % 12 + if (ampm == "pm") 12 else 0, m).atZone(tz).toInstant().toEpochMilli()
    if (month.isNotEmpty()) {
        val monthNumber = CLAUDE_USAGE_MONTHS.indexOf(month).takeIf { it >= 0 }?.plus(1) ?: return null
        val date = LocalDate.of(local.year, monthNumber, day.toInt())
        val reset = at(date)
        return if (reset >= now - 3_600_000) reset else at(LocalDate.of(local.year + 1, monthNumber, day.toInt()))
    }
    val date = local.toLocalDate()
    val reset = at(date)
    return if (reset >= now - 60_000) reset else at(date.plusDays(1))
}

private val CLAUDE_USAGE_WINDOWS = listOf(
    "5h" to Regex("^Current session: (\\d{1,3})% used · resets (.+)$", RegexOption.MULTILINE),
    "Week" to Regex("^Current week \\(all models\\): (\\d{1,3})% used · resets (.+)$", RegexOption.MULTILINE),
)
private val CLAUDE_USAGE_RESET = Regex("^(?:([A-Z][a-z]{2}) (\\d{1,2}), )?(\\d{1,2})(?::(\\d{2}))?(am|pm) \\(([^)]+)\\)$")
private val CLAUDE_USAGE_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val CLAUDE_USAGE_ANSI = Regex("\u001b\\[[0-9;?]*[ -/]*[@-~]|\u001b\\][^\u0007]*\u0007")
