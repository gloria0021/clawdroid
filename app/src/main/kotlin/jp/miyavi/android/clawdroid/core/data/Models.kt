package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.long
import jp.miyavi.android.clawdroid.core.gateway.obj
import jp.miyavi.android.clawdroid.core.gateway.string
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
) {
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
) {
    enum class Role { USER, ASSISTANT, TOOL_RESULT, SYSTEM }

    val text: String get() = parts.filterIsInstance<MessagePart.Text>().joinToString("\n\n") { it.markdown }
}

sealed interface MessagePart {
    data class Text(val markdown: String) : MessagePart
    data class Video(val url: String) : MessagePart
    data class Image(val url: String?, val base64: String?, val mimeType: String?) : MessagePart
    data class ToolCall(
        val callId: String?,
        val name: String,
        val detail: String?,
        val result: String?,
        val isError: Boolean,
        val isResult: Boolean,
    ) : MessagePart
}

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
        val parts = if (role == ChatMessage.Role.TOOL_RESULT) {
            listOfNotNull(toolPart(obj, isResult = true, topLevel = true))
        } else {
            contentParts(obj["content"])
        }
        // 利用者が添付した画像は本文ではなく __openclaw.media に入る（media://inbound/...）
        val attached = if (role == ChatMessage.Role.USER) {
            meta?.array("media").orEmpty().mapNotNull { el ->
                val media = el.asObject() ?: return@mapNotNull null
                val url = media.string("url") ?: return@mapNotNull null
                val image = media.string("kind") == "image" || media.string("contentType")?.startsWith("image/") == true
                if (image) MessagePart.Image(url = url, base64 = null, mimeType = media.string("contentType")) else null
            }
        } else emptyList()
        val entryId = meta?.string("id")
        return ChatMessage(
            id = entryId ?: "i$index-${obj.long("timestamp") ?: 0}",
            role = role,
            parts = attached.filterNot { a -> parts.any { it is MessagePart.Image && it.url == a.url } } + parts,
            timestamp = obj.long("timestamp"),
            // 利用者のメッセージは送信の idempotencyKey が run の識別（受け付け待ちとの突き合わせに使う）
            runId = (
                meta?.string("runId")
                    ?: if (role == ChatMessage.Role.USER) meta?.string("idempotencyKey") ?: obj.string("idempotencyKey") else obj.string("runId")
                )?.removeSuffix(":user"),
            isError = obj.bool("isError") == true || obj.string("stopReason") in setOf("error", "aborted"),
            truncated = meta?.bool("truncated") == true,
        )
    }

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
