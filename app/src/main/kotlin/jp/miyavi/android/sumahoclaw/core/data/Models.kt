package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.array
import jp.miyavi.android.sumahoclaw.core.gateway.asObject
import jp.miyavi.android.sumahoclaw.core.gateway.bool
import jp.miyavi.android.sumahoclaw.core.gateway.long
import jp.miyavi.android.sumahoclaw.core.gateway.obj
import jp.miyavi.android.sumahoclaw.core.gateway.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

// Gateway の写し（data-model 2）。項目名は ref-schema と公式 Android アプリの解析に合わせる。

data class Agent(
    val id: String,
    val name: String,
    val emoji: String?,
    val avatarUrl: String?,
    val defaultModel: String?,
    val isSystem: Boolean,
    val thinkingLevels: List<SettingChoice> = emptyList(),
    val thinkingDefault: String? = null,
    val defaultPermissionMode: String? = null,
    /** 作られた経緯（`operator` / `agent` / `template`。spec 004 FR-010）。Gateway が持っていなければ null。 */
    val createdVia: String? = null,
    val creatorAgentId: String? = null,
    val createdAt: Long? = null,
    val runtimeId: String? = null,
)

data class SessionSummary(
    val key: String,
    val agentId: String?,
    val sessionId: String?,
    /** 表示名。無ければ null（画面側で「新しいチャット」）。 */
    val title: String?,
    val cwd: String?,
    val model: String?,
    val thinkingLevel: String?,
    val running: Boolean,
    val unread: Boolean,
    val archived: Boolean,
    val isAutomation: Boolean,
    val updatedAt: Long?,
    val contextTokens: Long?,
    val totalTokens: Long?,
    val preview: String?,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokensFresh: Boolean = true,
    val modelReference: String? = null,
    val permissionMode: String? = null,
    val permissionModePending: Boolean = false,
    /** エージェントのホーム（main）のセッションか。 */
    val isMain: Boolean = false,
    /** ピン留め（サイドパネルのホームの下に出す）。 */
    val pinned: Boolean = false,
    /** 並行開発（ワークツリー）のブランチ。ワークツリーでなければ null。 */
    val worktreeBranch: String? = null,
    /** ワークツリーの元のリポジトリ（`worktree.canonicalWorkspaceDir` / `repoRoot`）。フォルダ別でここにまとめる。 */
    val worktreeRoot: String? = null,
    val runtimeId: String? = null,
) {
    /** フォルダ別でまとめる先。ワークツリーは元のリポジトリにぶら下げる（マスター指摘 2026-11-24）。 */
    val projectFolder: String? get() = (worktreeRoot ?: cwd)?.trimEnd('/')

    val contextFraction: Float?
        get() {
            val limit = contextTokens?.takeIf { it > 0 } ?: return null
            val used = totalTokens?.takeIf { totalTokensFresh && it >= 0 } ?: return null
            return (used.toDouble() / limit).coerceAtMost(1.0).toFloat()
        }
}

enum class SessionSetting(val wireKey: String) {
    MODEL("model"),
    EFFORT("thinkingLevel"),
    PERMISSIONS("permissionMode"),
}

data class SettingChoice(val id: String, val label: String)
data class ModelChoice(
    val reference: String,
    val name: String,
    val selectable: Boolean,
    val thinkingLevels: List<SettingChoice>,
    val thinkingDefault: String?,
)

data class ModelCatalog(val models: List<ModelChoice> = emptyList(), val canReset: Boolean = true) {
    fun find(reference: String?): ModelChoice? {
        if (reference == null) return null
        return models.firstOrNull { it.reference == reference }
            ?: models.singleOrNull { it.reference.substringAfter('/') == reference }
    }
}

/**
 * スラッシュコマンド（`commands.list` の 1 件）。`name` は先頭の `/` を除いた主な名前、`aliases` はほかの呼び方（こちらも `/` なし）。
 */
data class SlashCommand(
    val name: String,
    val aliases: List<String>,
    val description: String,
    val acceptsArgs: Boolean,
    val args: List<SlashArg> = emptyList(),
    /** スキルのコマンドなら、その表示名（スキルでなければ null）。 */
    val skillName: String? = null,
    /** スキルをモデルに見せているか（`$` の補完に出すのはこれだけ）。 */
    val skillModelVisible: Boolean = false,
) {
    val allNames: List<String> get() = listOf(name) + aliases
    val isSkill: Boolean get() = skillName != null
}

/** コマンドの引数。`choices` は選べる値（無ければ自由入力）。 */
data class SlashArg(val name: String, val description: String, val choices: List<SettingChoice>)

data class ProviderUsage(
    val name: String,
    val windows: List<UsageWindow>,
    val plan: String? = null,
    /** この使用量に当たるプロバイダの id（CLI の別名を含む。例: claude-cli と anthropic）。 */
    val ids: List<String> = emptyList(),
)
data class UsageWindow(val label: String, val usedFraction: Float, val resetAt: Long?)

data class ChatMessage(
    /** 表示の key。transcript の id があればそれ、無ければ位置から作る。 */
    val id: String,
    val role: Role,
    val parts: List<MessagePart>,
    val timestamp: Long?,
    val runId: String?,
    val isError: Boolean,
    val truncated: Boolean,
    /** 利用者のメッセージを送ったときに選ばれていたモデル（`__openclaw.modelSelection`）。 */
    val modelSelection: ModelSelection? = null,
    /** モデルの切り替えの記録（`custom` の `session-model-change`）。 */
    val modelChange: ModelChange? = null,
    /** 会話ではなくシステムのお知らせ（コンテキストの圧縮など）。吹き出しではなく区切りで出す。 */
    val notice: SystemNotice? = null,
) {
    enum class Role { USER, ASSISTANT, TOOL_RESULT, SYSTEM }

    val text: String get() = parts.filterIsInstance<MessagePart.Text>().joinToString("\n\n") { it.markdown }
}

sealed interface MessagePart {
    data class Text(val markdown: String) : MessagePart
    data class Video(val url: String) : MessagePart
    /** [preloaded] は手元ですでに縮めて復号した画像（ファイルビューアから全画面で開くとき）。 */
    data class Image(val url: String?, val base64: String?, val mimeType: String?, val preloaded: androidx.compose.ui.graphics.ImageBitmap? = null) : MessagePart
    /**
     * 画像でない添付ファイル。利用者の添付（`media://inbound/...`）と、エージェントが `MEDIA:` で示したホストのパス。
     * [name] は表示名（無ければパスの最後）、[size] は分かればバイト数。
     */
    data class File(val url: String, val name: String, val mimeType: String?, val size: Long?) : MessagePart
    /** Gateway が添付を受け付けなかった（`attachment_error`。対応していない形式・大きすぎる・見つからない）。 */
    data class AttachmentError(val name: String, val code: String?) : MessagePart
    data class ToolCall(
        val callId: String?,
        val name: String,
        val detail: String?,
        val result: String?,
        val isError: Boolean,
        val isResult: Boolean,
    ) : MessagePart
}

/** 会話の中に入る、システムのお知らせ（Control UI の system notice / divider に当たるもの）。 */
sealed interface SystemNotice {
    /** コンテキストの圧縮（OpenClaw の圧縮の記録、または Claude CLI の要約）。トークン数は分かるときだけ。 */
    data class Compaction(val tokensBefore: Long?, val tokensAfter: Long?) : SystemNotice
    /** CLI の実行環境が会話に足した文脈。 */
    data object InjectedContext : SystemNotice
    /** バックグラウンドのタスクの知らせ。 */
    data object TaskNotification : SystemNotice
    /** セッションのリセット。送ったコマンドで分かるときは種類を持つ。 */
    data class Reset(val kind: ResetKind) : SystemNotice
}

enum class ResetKind {
    /** `/reset`（履歴を空にして始め直す）。 */
    RESET,
    /** `/reset soft`（履歴を残したまま始め直す）。 */
    SOFT,
    /** `/new`。 */
    NEW,
    /** Gateway の記録だけで、どのリセットか分からない（自動の切り替えなど）。 */
    UNKNOWN,
}

/**
 * Gateway のホストのメモリとディスク（`system.info`）。空きは Gateway が数えた「すぐ使える量」（Linux では MemAvailable）で、
 * 使用は全体から空きを引いたもの（Control UI と同じ数え方）。ディスクは OpenClaw の状態を置いている所の 1 つ。
 */
data class HostStats(val memoryTotalBytes: Long, val memoryFreeBytes: Long, val diskTotalBytes: Long, val diskAvailableBytes: Long) {
    val memoryUsedFraction: Float get() = usedFraction(memoryTotalBytes, memoryFreeBytes)
    val diskUsedFraction: Float get() = usedFraction(diskTotalBytes, diskAvailableBytes)

    private fun usedFraction(total: Long, free: Long) = ((total - free).toFloat() / total).coerceIn(0f, 1f)
}

/** 送信時のモデル（`provider/model`）と、その直前のモデル。 */
data class ModelSelection(val current: String, val previous: String?)

/** モデルの切り替え（`provider/model`）。 */
data class ModelChange(val from: String, val to: String)

/** 生成中の応答（chat の delta を組み立てたもの）。 */
data class LiveRun(val runId: String, val text: String)

// ---- 解析 ----

internal object Parse {
    fun agent(obj: JsonObject): Agent? {
        val id = obj.string("id") ?: return null
        val identity = obj.obj("identity")
        return Agent(
            id = id,
            name = identity?.string("name") ?: obj.string("name") ?: id,
            emoji = identity?.string("emoji"),
            avatarUrl = identity?.string("avatarUrl") ?: identity?.string("avatar"),
            defaultModel = obj.obj("model")?.string("primary"),
            isSystem = obj.string("kind") == "system",
            thinkingLevels = thinkingChoices(obj).ifEmpty {
                obj.array("thinkingOptions").mapNotNull { option ->
                    (option as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let { SettingChoice(it, it) }
                }
            },
            thinkingDefault = obj.string("thinkingDefault"),
            defaultPermissionMode = obj.string("defaultPermissionMode"),
            createdVia = obj.string("createdVia"),
            creatorAgentId = obj.string("creatorAgentId"),
            createdAt = obj.long("createdAt"),
            runtimeId = obj.obj("agentRuntime")?.string("id"),
        )
    }

    fun session(obj: JsonObject?): SessionSummary? {
        if (obj == null) return null
        val key = obj.string("key") ?: obj.string("sessionKey") ?: return null
        val status = obj.string("status")
        return SessionSummary(
            key = key,
            agentId = obj.string("agentId"),
            sessionId = obj.string("sessionId"),
            title = listOf("label", "displayName", "derivedTitle", "autoLabel")
                .firstNotNullOfOrNull { k -> obj.string(k)?.trim()?.takeIf(String::isNotEmpty) },
            cwd = listOf("execCwd", "spawnedCwd", "workspaceDir").firstNotNullOfOrNull { obj.string(it)?.takeIf(String::isNotBlank) },
            model = obj.string("activeModel") ?: obj.string("model"),
            thinkingLevel = obj.string("thinkingLevel"),
            running = obj.bool("hasActiveRun") == true || status == "running" || status == "queued",
            unread = obj.bool("unread") == true,
            archived = obj.bool("archived") == true,
            isAutomation = obj.string("createdVia") == "cron",
            isMain = obj.bool("isMain") == true || key.endsWith(":main"),
            pinned = obj.bool("pinned") == true,
            worktreeBranch = obj.obj("worktree")?.string("branch")?.takeIf(String::isNotBlank),
            worktreeRoot = obj.obj("worktree")?.let { w -> w.string("canonicalWorkspaceDir") ?: w.string("repoRoot") }?.takeIf(String::isNotBlank),
            updatedAt = obj.long("lastActivityAt") ?: obj.long("updatedAt"),
            contextTokens = obj.long("contextTokens"),
            totalTokens = obj.long("totalTokens"),
            preview = obj.string("lastMessagePreview"),
            inputTokens = obj.long("inputTokens"),
            outputTokens = obj.long("outputTokens"),
            totalTokensFresh = obj.bool("totalTokensFresh") != false,
            modelReference = obj.string("model")?.let { canonicalModel(it, obj.string("modelProvider")) },
            permissionMode = obj.string("permissionMode"),
            permissionModePending = obj.bool("permissionModePending") == true,
            runtimeId = obj.obj("agentRuntime")?.string("id"),
        )
    }

    fun modelCatalog(obj: JsonObject): ModelCatalog {
        val models = obj.array("models").mapNotNull { value ->
            val model = value.asObject() ?: return@mapNotNull null
            val id = model.string("id") ?: return@mapNotNull null
            ModelChoice(
                reference = canonicalModel(id, model.string("provider")),
                name = model.string("name") ?: id,
                selectable = model.bool("manualSelectionAllowed") != false && model.bool("available") != false,
                thinkingLevels = thinkingChoices(model),
                thinkingDefault = model.string("thinkingDefault"),
            )
        }.distinctBy(ModelChoice::reference)
        val policy = obj.obj("modelSelectionPolicy")
        return ModelCatalog(models, policy == null || policy.string("defaultModel") != null)
    }

    /** `commands.list`（scope = text）の結果。ネイティブ専用のコマンドは除く。 */
    fun slashCommands(obj: JsonObject): List<SlashCommand> = obj.array("commands").mapNotNull { value ->
        val command = value.asObject() ?: return@mapNotNull null
        if (command.string("scope") == "native") return@mapNotNull null
        val aliases = command.array("textAliases").mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.removePrefix("/")?.takeIf(String::isNotEmpty) }
        val name = command.string("name")?.trim()?.removePrefix("/")?.takeIf(String::isNotEmpty) ?: aliases.firstOrNull() ?: return@mapNotNull null
        SlashCommand(
            name = name,
            aliases = aliases.filter { it != name }.distinct(),
            description = command.string("description").orEmpty(),
            acceptsArgs = command.bool("acceptsArgs") == true,
            args = command.array("args").mapNotNull { argValue ->
                val arg = argValue.asObject() ?: return@mapNotNull null
                SlashArg(
                    name = arg.string("name") ?: return@mapNotNull null,
                    description = arg.string("description").orEmpty(),
                    choices = arg.array("choices").mapNotNull { choiceValue ->
                        val choice = choiceValue.asObject() ?: return@mapNotNull null
                        val id = choice.string("value") ?: return@mapNotNull null
                        SettingChoice(id, choice.string("label") ?: id)
                    },
                )
            },
            skillName = if (command.string("source") == "skill") command.string("skillDisplayName")?.trim()?.takeIf(String::isNotEmpty) ?: name else null,
            skillModelVisible = command.bool("skillModelVisible") != false,
        )
    }.distinctBy(SlashCommand::name)

    private fun thinkingChoices(obj: JsonObject): List<SettingChoice> =
        obj.array("thinkingLevels").mapNotNull { value ->
            val option = value.asObject() ?: return@mapNotNull null
            val id = option.string("id") ?: return@mapNotNull null
            SettingChoice(id, option.string("label") ?: id)
        }.distinctBy(SettingChoice::id)

    private fun canonicalModel(id: String, provider: String?): String =
        if (provider == null || id.startsWith("$provider/")) id else "$provider/$id"

    /**
     * `models.authStatus` の各プロバイダの `usage`（Control UI の入力欄の使用量と同じ出どころ）。
     * Claude Code CLI（claude-cli）などの CLI の認証もここに入り、`usage.status` には出ない。
     * 同じ中身（名前・窓）の行は 1 つにまとめ、id を合わせて持つ。
     */
    fun authStatusUsage(obj: JsonObject): List<ProviderUsage> {
        val groups = LinkedHashMap<String, ProviderUsage>()
        obj.array("providers").forEach { entry ->
            val provider = entry.asObject() ?: return@forEach
            val usage = provider.obj("usage") ?: return@forEach
            val windows = usageWindows(usage)
            if (windows.isEmpty()) return@forEach
            val ids = listOfNotNull(provider.string("provider"), usage.string("providerId")).distinct()
            val name = provider.string("displayName") ?: usage.string("displayName") ?: ids.firstOrNull() ?: return@forEach
            val identity = name + windows.joinToString { "${it.label}:${it.usedFraction}" }
            val existing = groups[identity]
            groups[identity] = existing?.copy(ids = (existing.ids + ids).distinct())
                ?: ProviderUsage(name, windows, usage.string("plan")?.takeIf(String::isNotBlank), ids)
        }
        return groups.values.toList()
    }

    private fun usageWindows(usage: JsonObject): List<UsageWindow> = usage.array("windows").mapNotNull { entryWindow ->
        val window = entryWindow.asObject() ?: return@mapNotNull null
        val label = window.string("label") ?: return@mapNotNull null
        val percent = (window["usedPercent"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: return@mapNotNull null
        UsageWindow(label, (percent / 100).coerceIn(0.0, 1.0).toFloat(), window.long("resetAt"))
    }

    fun providerUsage(obj: JsonObject): List<ProviderUsage> =
        obj.array("providers").mapNotNull { entry ->
            val provider = entry.asObject() ?: return@mapNotNull null
            val name = provider.string("displayName") ?: provider.string("provider") ?: return@mapNotNull null
            val windows = provider.array("windows").mapNotNull window@{ entryWindow ->
                val window = entryWindow.asObject() ?: return@window null
                val label = window.string("label") ?: return@window null
                val percent = (window["usedPercent"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
                    ?: return@window null
                UsageWindow(label, (percent / 100).coerceIn(0.0, 1.0).toFloat(), window.long("resetAt"))
            }
            name.takeIf { windows.isNotEmpty() }?.let { ProviderUsage(it, windows, provider.string("plan")?.takeIf(String::isNotBlank)) }
        }

    fun message(obj: JsonObject, index: Int): ChatMessage? {
        val role = when (obj.string("role")?.trim()?.lowercase()) {
            "user" -> ChatMessage.Role.USER
            "assistant" -> ChatMessage.Role.ASSISTANT
            "tool", "tool_result", "toolresult" -> ChatMessage.Role.TOOL_RESULT
            "system", "custom" -> ChatMessage.Role.SYSTEM
            else -> return null
        }
        val meta = obj.obj("__openclaw")
        val parts = when (role) {
            ChatMessage.Role.TOOL_RESULT -> listOfNotNull(toolPart(obj, isResult = true, topLevel = true))
            // エージェントが本文の `MEDIA:` の行で示した画像は、画像として出す（Control UI と同じ）
            ChatMessage.Role.ASSISTANT -> mediaDirectives(contentParts(obj["content"]))
            else -> contentParts(obj["content"])
        }
        // 利用者が添付した画像・ファイルは本文ではなく __openclaw.media に入る（media://inbound/...）
        val attached = if (role == ChatMessage.Role.USER) attachedMedia(meta?.array("media").orEmpty()) else emptyList()
        if (hidden(obj, meta, role, parts)) return null
        val entryId = meta?.string("id")
        val notice = notice(obj, meta, role, parts)
        val selection = if (role == ChatMessage.Role.USER && notice == null) meta?.obj("modelSelection") else null
        val modelChange = obj.takeIf { it.string("customType") == "session-model-change" }?.obj("details")?.let { details ->
            val from = details.string("from")?.takeIf(String::isNotBlank) ?: return@let null
            val to = details.string("to")?.takeIf(String::isNotBlank) ?: return@let null
            ModelChange(from, to)
        }
        return ChatMessage(
            id = entryId ?: "i$index-${obj.long("timestamp") ?: 0}",
            // 利用者の発言の形で届くお知らせは、利用者のメッセージとして扱わない（実行の始まりや返信の突き合わせに混ぜない）
            role = if (notice != null) ChatMessage.Role.SYSTEM else role,
            parts = attached.filterNot { a -> a is MessagePart.Image && parts.any { it is MessagePart.Image && it.url == a.url } } + parts,
            timestamp = obj.long("timestamp"),
            // 利用者のメッセージは送信の idempotencyKey が run の識別（受け付け待ちとの突き合わせに使う）
            runId = (
                meta?.string("runId")
                    ?: if (role == ChatMessage.Role.USER) meta?.string("idempotencyKey") ?: obj.string("idempotencyKey") else obj.string("runId")
                )?.removeSuffix(":user"),
            isError = obj.bool("isError") == true || obj.string("stopReason") in setOf("error", "aborted"),
            truncated = meta?.bool("truncated") == true,
            modelSelection = selectedModel(selection)?.let { ModelSelection(it, selectedModel(selection?.obj("previous"))) },
            modelChange = modelChange,
            notice = notice,
        )
    }

    /**
     * システムのお知らせか。`__openclaw.kind = compaction`（OpenClaw の圧縮）と、
     * `provenance.kind = internal_system` の CLI の文脈・タスクの知らせ（Control UI と同じ見分け方）。
     * Claude CLI の圧縮の要約も cli_harness_context で届くので、要約の書き出しで圧縮と分かる。
     * リセットは、Gateway の区切り（`__openclaw.kind = reset`）と、利用者が送った `/reset`・`/reset soft`・`/new` だけの発言。
     */
    private fun notice(obj: JsonObject, meta: JsonObject?, role: ChatMessage.Role, parts: List<MessagePart>): SystemNotice? {
        when (meta?.string("kind")) {
            "compaction" -> return SystemNotice.Compaction(meta.long("tokensBefore"), meta.long("tokensAfter"))
            "reset" -> return SystemNotice.Reset(ResetKind.UNKNOWN)
        }
        if (role == ChatMessage.Role.USER) {
            resetCommandKind(parts.filterIsInstance<MessagePart.Text>().singleOrNull()?.markdown)?.let { return SystemNotice.Reset(it) }
        }
        val provenance = obj.obj("provenance") ?: meta?.obj("provenance") ?: return null
        if (provenance.string("kind") != "internal_system") return null
        return when (provenance.string("sourceTool")) {
            "cli_harness_context" -> {
                val text = parts.filterIsInstance<MessagePart.Text>().firstOrNull()?.markdown.orEmpty().trimStart()
                if (text.startsWith(CLI_COMPACT_SUMMARY_LEAD)) SystemNotice.Compaction(null, null) else SystemNotice.InjectedContext
            }
            "claude_cli_task_notification" -> SystemNotice.TaskNotification
            else -> null
        }
    }

    /**
     * 画面に出さない記録。Claude CLI のスラッシュコマンドの出力（`/compact` のあとの `<local-command-stdout>Compacted …` など）と、
     * 圧縮の成功を知らせる Gateway の一言（要約の区切りが代わりに出る。失敗・見送りは残す）。
     */
    private fun hidden(obj: JsonObject, meta: JsonObject?, role: ChatMessage.Role, parts: List<MessagePart>): Boolean {
        val text = parts.filterIsInstance<MessagePart.Text>().firstOrNull()?.markdown?.trimStart() ?: return false
        return when (role) {
            ChatMessage.Role.USER -> (meta?.string("importedFrom") == "claude-cli" && CLI_LOCAL_COMMAND_TAGS.any(text::startsWith)) ||
                text.startsWith(SESSION_STARTUP_PROMPT_LEAD)
            ChatMessage.Role.ASSISTANT -> text.trim() in GATEWAY_RESET_ACKS || (obj.string("model") == "gateway-injected" &&
                text.trimStart('⚙', '️', ' ').let { rest -> GATEWAY_COMPACTED_LEADS.any(rest::startsWith) })
            else -> false
        }
    }

    /** `/reset`・`/reset soft`・`/new` だけの発言なら、その種類。続けて文を書いたもの（`/reset soft 〜`）はふつうの発言のまま。 */
    fun resetCommandKind(text: String?): ResetKind? {
        val words = text?.trim()?.lowercase()?.split(Regex("\\s+")) ?: return null
        return when (words) {
            listOf("/reset") -> ResetKind.RESET
            listOf("/reset", "soft") -> ResetKind.SOFT
            listOf("/new") -> ResetKind.NEW
            else -> null
        }
    }

    /** リセットのあとに OpenClaw がエージェントへ渡す、始め直しの指示の書き出し（session-reset-prompt の文言）。 */
    private const val SESSION_STARTUP_PROMPT_LEAD = "A new session was started via /new or /reset"

    /** Gateway の `/reset`・`/new` の受け付けの返事（commands-handlers の文言）。区切りが代わりに出る。 */
    private val GATEWAY_RESET_ACKS = setOf("✅ Session reset.", "✅ New session started.")

    private val CLI_LOCAL_COMMAND_TAGS = listOf("<local-command-stdout>", "<local-command-stderr>", "<local-command-caveat>")

    /** Gateway の `/compact` の成功の返事の書き出し（commands-handlers の文言）。 */
    private val GATEWAY_COMPACTED_LEADS = listOf("Compaction finished", "Compacted", "Server-side compaction")

    /** Claude CLI が圧縮のあとに入れる要約の書き出し。 */
    private const val CLI_COMPACT_SUMMARY_LEAD = "This session is being continued from a previous conversation"

    /** `system.info` のメモリとディスク。どちらかが欠けていれば null。 */
    fun hostStats(obj: JsonObject?): HostStats? {
        obj ?: return null
        val memoryTotal = obj.long("memoryTotalBytes")?.takeIf { it > 0 } ?: return null
        val memoryFree = obj.long("memoryFreeBytes")?.takeIf { it >= 0 } ?: return null
        val diskTotal = obj.long("diskTotalBytes")?.takeIf { it > 0 } ?: return null
        val diskAvailable = obj.long("diskAvailableBytes")?.takeIf { it >= 0 } ?: return null
        return HostStats(memoryTotal, memoryFree.coerceAtMost(memoryTotal), diskTotal, diskAvailable.coerceAtMost(diskTotal))
    }

    /** `{ provider, model }` を `provider/model` にする（Control UI と同じく両方そろったときだけ）。 */
    private fun selectedModel(obj: JsonObject?): String? {
        val provider = obj?.string("provider")?.takeIf(String::isNotBlank) ?: return null
        val model = obj.string("model")?.takeIf(String::isNotBlank) ?: return null
        return canonicalModel(model, provider)
    }

    /**
     * 本文の `MEDIA:<パス>` の行（OpenClaw の添付の書き方）のうち画像を、本文から外して画像の部品にする（本文の後ろに並べる）。
     * コードの囲みの中はそのまま。画像でないファイルの行は本文に残す。
     */
    /** `__openclaw.media` の並びを画像とファイルの部品にする（利用者の添付・受け付け待ちの発言）。 */
    internal fun attachedMedia(media: List<JsonElement>): List<MessagePart> = media.mapNotNull { el ->
        val obj = el.asObject() ?: return@mapNotNull null
        val url = obj.string("url") ?: return@mapNotNull null
        val type = obj.string("contentType")
        if (obj.string("kind") == "image" || type?.startsWith("image/") == true) {
            MessagePart.Image(url = url, base64 = null, mimeType = type)
        } else {
            MessagePart.File(url, obj.string("fileName")?.takeIf(String::isNotBlank) ?: fileNameOf(url), type, obj.long("sizeBytes"))
        }
    }

    /** パスの最後を表示名にする（添付の間に入る `---<uuid>` は外す。Control UI と同じ）。 */
    internal fun fileNameOf(url: String): String =
        url.substringBefore('?').trimEnd('/').substringAfterLast('/').replace(INBOUND_ID_SUFFIX, "").ifBlank { url }

    private val INBOUND_ID_SUFFIX = Regex("---[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=\\.[^./]*$|$)", RegexOption.IGNORE_CASE)

    internal fun mediaDirectives(parts: List<MessagePart>): List<MessagePart> {
        if (parts.none { it is MessagePart.Text && "MEDIA:" in it.markdown }) return parts
        val images = mutableListOf<MessagePart>()
        val kept = parts.mapNotNull { part ->
            if (part !is MessagePart.Text || "MEDIA:" !in part.markdown) return@mapNotNull part
            var fenced = false
            val lines = part.markdown.lines().filter { line ->
                if (line.trimStart().startsWith("```")) fenced = !fenced
                val source = if (fenced) null else MEDIA_LINE.matchEntire(line)?.groupValues?.get(1)?.trim('`', '"', '\'', ' ')
                    ?.takeIf(String::isNotEmpty)
                when {
                    source == null -> Unit
                    mediaImageType(source) != null -> images += MessagePart.Image(url = source, base64 = null, mimeType = mediaImageType(source))
                    // 画像でないファイルは添付ファイルとして出す（Gateway から取れる場所だけ。Web の URL は本文に残す）
                    jp.miyavi.android.sumahoclaw.core.gateway.GatewayClient.isGatewayMediaSource(source) -> images += MessagePart.File(source, fileNameOf(source), null, null)
                    else -> return@filter true
                }
                source == null
            }
            lines.joinToString("\n").trim().takeIf(String::isNotEmpty)?.let(MessagePart::Text)
        }
        return kept + images.distinctBy { (it as? MessagePart.Image)?.url ?: (it as MessagePart.File).url }
    }

    /** 画像の拡張子ならその種類（端末で復号できるものだけ）。 */
    private fun mediaImageType(source: String): String? =
        when (source.substringBefore('?').substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "avif" -> "image/avif"
            else -> null
        }

    private val MEDIA_LINE = Regex("^\\s*MEDIA:\\s*(\\S.*?)\\s*$")

    fun contentParts(content: JsonElement?): List<MessagePart> = when (content) {
        is JsonPrimitive -> listOfNotNull(content.contentOrNull?.takeIf(String::isNotBlank)?.let(MessagePart::Text))
        is JsonArray -> content.mapNotNull { part(it as? JsonObject ?: return@mapNotNull null) }
        else -> emptyList()
    }

    private fun part(obj: JsonObject): MessagePart? = when (normalizeType(obj.string("type") ?: "text")) {
        "text", "input_text", "output_text" -> (obj.string("text") ?: obj.string("content"))
            ?.takeIf(String::isNotBlank)?.let(MessagePart::Text)
        "image", "image_url", "input_image", "output_image" -> {
            val source = obj.obj("source")
            MessagePart.Image(
                url = mediaUrl(obj, "image_url"),
                base64 = obj.string("data") ?: source?.string("data") ?: obj.string("content"),
                mimeType = obj.string("mimeType") ?: source?.string("media_type"),
            )
        }
        "video", "video_url", "input_video", "output_video" -> mediaUrl(obj, "video_url")?.let(MessagePart::Video)
        // Gateway が管理する添付（エージェントの MEDIA: を Gateway が受け付けたもの。Web 版のファイルのカードと同じ）
        "attachment" -> obj.obj("attachment")?.let { a ->
            val url = a.string("url") ?: a.string("openUrl") ?: return@let null
            val type = a.string("mimeType")
            if (a.string("kind") == "image" || type?.startsWith("image/") == true) MessagePart.Image(url = url, base64 = null, mimeType = type)
            else MessagePart.File(url, a.string("label")?.takeIf(String::isNotBlank) ?: fileNameOf(url), type, a.long("sizeBytes"))
        }
        "attachment_error" -> obj.obj("attachment")?.let { a ->
            MessagePart.AttachmentError(a.string("label")?.takeIf(String::isNotBlank) ?: "", a.string("code"))
        }
        "toolCall" -> toolPart(obj, isResult = false, topLevel = false)
        "toolResult" -> toolPart(obj, isResult = true, topLevel = false)
        else -> null
    }

    private fun mediaUrl(obj: JsonObject, field: String): String? =
        obj.string("url") ?: obj.string(field) ?: obj.obj(field)?.string("url") ?: obj.obj("source")?.string("url")

    private fun normalizeType(type: String): String = when (type.lowercase()) {
        "toolcall", "tool_call", "tooluse", "tool_use" -> "toolCall"
        "toolresult", "tool_result", "tool_result_block" -> "toolResult"
        else -> type
    }

    private fun toolPart(obj: JsonObject, isResult: Boolean, topLevel: Boolean): MessagePart.ToolCall? {
        val name = listOf("name", "toolName", "tool_name").firstNotNullOfOrNull { obj.string(it)?.takeIf(String::isNotBlank) }.orEmpty()
        val id = listOf("toolCallId", "tool_call_id", "toolUseId", "tool_use_id", "callId").firstNotNullOfOrNull(obj::string)
            ?: if (topLevel) null else obj.string("id")
        if (name.isEmpty() && id == null) return null
        val args = if (topLevel) null else (obj["arguments"] ?: obj["input"] ?: obj["args"]) as? JsonObject
        val detail = args?.let { a ->
            listOf("description", "command", "path", "file_path", "url", "query").firstNotNullOfOrNull { a.string(it) }
                ?: a.toString().take(TOOL_DETAIL_MAX)
        }
        val result = if (isResult) toolResultText(obj["content"] ?: obj["result"] ?: obj["text"])?.take(TOOL_RESULT_MAX) else null
        return MessagePart.ToolCall(
            callId = id,
            name = name.ifEmpty { "tool" },
            detail = detail?.take(TOOL_DETAIL_MAX),
            result = result,
            isError = obj.bool("isError") == true || obj.bool("is_error") == true,
            isResult = isResult,
        )
    }

    private fun toolResultText(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.mapNotNull { (it as? JsonObject)?.string("text") }.joinToString("\n").ifBlank { null }
        is JsonObject -> element.string("text") ?: element.toString()
        else -> null
    }

    fun sessionsArray(root: JsonObject?): List<SessionSummary> =
        root?.array("sessions")?.mapNotNull { session(it as? JsonObject) }.orEmpty()

    const val TOOL_DETAIL_MAX = 600
    const val TOOL_RESULT_MAX = 2_000
}

/**
 * 画面に出す短いモデル名。`anthropic/claude-opus-5-5` → `Opus 5.5`、`openai/gpt-5.5` → `GPT 5.5`。
 * 名前の読み取りに失敗したら、プロバイダを除いた id をそのまま返す。
 */
fun shortModelName(reference: String): String {
    val id = reference.substringAfterLast('/').trim()
    val words = id.removePrefix("claude-").split('-').filter(String::isNotBlank)
    if (words.isEmpty()) return id
    val nameWords = words.takeWhile { w -> w.firstOrNull()?.isDigit() != true }
    val version = words.drop(nameWords.size).takeWhile { w -> w.all { it.isDigit() || it == '.' } }
    if (nameWords.isEmpty()) return id
    val rest = words.drop(nameWords.size + version.size)
    fun word(w: String) = if (w.lowercase() in MODEL_ACRONYMS) w.uppercase() else w.replaceFirstChar(Char::uppercase)
    return listOfNotNull(
        nameWords.joinToString(" ", transform = ::word),
        version.takeIf { it.isNotEmpty() }?.joinToString("."),
        rest.takeIf { it.isNotEmpty() }?.joinToString(" ", transform = ::word),
    ).joinToString(" ")
}

/** 大文字で書くモデル名の略語。 */
private val MODEL_ACRONYMS = setOf("gpt", "glm", "oss")

/**
 * コンテキストの内訳（Control UI と同じ計算）。どれも文字数 ÷ 4 の見積もりで、取れない項目は null。
 * `sessions.usage` の `contextWeight`（システムプロンプトの報告）から作る。
 */
data class ContextBreakdown(
    val messages: Long?,
    val files: Long?,
    val tools: Long?,
    val skills: Long?,
    val system: Long?,
)

internal object ContextBreakdownParser {
    private fun estimate(chars: Long) = Math.round(chars / 4.0)

    fun parse(report: JsonObject, used: Long): ContextBreakdown {
        val files = report.array("injectedWorkspaceFiles").mapNotNull { it.asObject() }
        val fileChars: Long? = if (files.any { it["injectedChars"] == null || it.long("injectedChars") == null }) null
        else files.sumOf { it.long("injectedChars") ?: 0 }
        val systemChars = report.obj("systemPrompt")?.long("chars") ?: 0
        val tools = report.obj("tools")
        val schemaChars = tools?.long("schemaChars") ?: 0
        val listChars = tools?.long("listChars") ?: 0
        val skillChars = report.obj("skills")?.long("promptChars") ?: 0
        val fixed = estimate(systemChars + schemaChars)
        return ContextBreakdown(
            messages = if (fileChars == null || fixed > used) null else used - fixed,
            files = fileChars?.let(::estimate),
            tools = estimate(schemaChars + listChars),
            skills = estimate(skillChars),
            system = fileChars?.let { estimate(maxOf(0, systemChars - it - skillChars - listChars)) },
        )
    }
}

/** プロバイダのロゴ（Hugeicons の各社のマーク）。分からなければ null。 */
fun providerBrand(ids: List<String>, name: String?): ProviderBrand? {
    val keys = (ids + listOfNotNull(name)).map { it.lowercase() }
    fun any(vararg words: String) = keys.any { k -> words.any { k.contains(it) } }
    return when {
        any("anthropic", "claude") -> ProviderBrand.CLAUDE
        any("openai", "codex", "chatgpt", "gpt") -> ProviderBrand.OPENAI
        any("google", "gemini") -> ProviderBrand.GEMINI
        any("deepseek") -> ProviderBrand.DEEPSEEK
        any("xai", "grok") -> ProviderBrand.GROK
        any("mistral") -> ProviderBrand.MISTRAL
        // ollama は llama を含むので、llama は先頭一致だけ
        any("meta") || keys.any { it.startsWith("llama") } -> ProviderBrand.META
        any("perplexity") -> ProviderBrand.PERPLEXITY
        any("qwen", "alibaba", "dashscope") -> ProviderBrand.QWEN
        else -> null
    }
}

enum class ProviderBrand { CLAUDE, OPENAI, GEMINI, DEEPSEEK, GROK, MISTRAL, META, PERPLEXITY, QWEN }
