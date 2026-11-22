package jp.miyavi.android.clawdroid.core.data

import jp.miyavi.android.clawdroid.core.gateway.ConnectionState
import jp.miyavi.android.clawdroid.core.gateway.GatewayClient
import jp.miyavi.android.clawdroid.core.gateway.array
import jp.miyavi.android.clawdroid.core.gateway.asObject
import jp.miyavi.android.clawdroid.core.gateway.bool
import jp.miyavi.android.clawdroid.core.gateway.long
import jp.miyavi.android.clawdroid.core.gateway.string
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** エージェントからの質問（`ask_user`。Gateway の QuestionRecord）。1 件に 1〜3 問。 */
internal data class PendingQuestion(
    val id: String,
    val questions: List<QuestionItem>,
    val agentId: String?,
    val sessionKey: String?,
    val createdAt: Long,
    val expiresAt: Long,
) {
    /** 秘密の入力・ファイルの選択・フォームなど、このアプリでは答えられない形が含まれるか。 */
    val answerable: Boolean get() = questions.all { it.answerable }

    /** この会話への質問か（会話の無い質問はどの会話にも出す）。 */
    fun belongsTo(sessionKey: String?): Boolean = this.sessionKey == null || this.sessionKey == sessionKey
}

internal data class QuestionItem(
    val questionId: String,
    val header: String,
    val question: String,
    val options: List<QuestionOptionItem>,
    val multiSelect: Boolean,
    val allowEmpty: Boolean,
    val answerable: Boolean,
)

/** 選択肢。`value` は答えとして送る値（無ければ `label`）。 */
internal data class QuestionOptionItem(val label: String, val description: String?, val value: String)

internal fun parsePendingQuestion(payload: Any?): PendingQuestion? {
    val row = payload as? JsonObject ?: return null
    val id = row.string("id")?.takeIf(String::isNotBlank) ?: return null
    if (row.string("status")?.let { it != "pending" } == true) return null
    val created = row.long("createdAtMs") ?: return null
    val expires = row.long("expiresAtMs") ?: return null
    val questions = row.array("questions").mapNotNull { element ->
        val q = element.asObject() ?: return@mapNotNull null
        val questionId = q.string("questionId") ?: return@mapNotNull null
        QuestionItem(
            questionId = questionId,
            header = q.string("header").orEmpty(),
            question = q.string("question").orEmpty(),
            options = q.array("options").mapNotNull { optionElement ->
                val option = optionElement.asObject() ?: return@mapNotNull null
                val label = option.string("label") ?: return@mapNotNull null
                QuestionOptionItem(label, option.string("description")?.takeIf(String::isNotBlank), option.string("value") ?: label)
            },
            multiSelect = q.bool("multiSelect") == true,
            allowEmpty = q.bool("allowEmpty") == true,
            answerable = q.bool("isSecret") != true && q["secretStore"] == null && q["resource"] == null && q.string("presentation") == null,
        )
    }
    if (questions.isEmpty()) return null
    return PendingQuestion(id, questions, row.string("agentId"), row.string("sessionKey"), created, expires)
}

/**
 * 待っている質問（アプリ全体で 1 つ）。Gateway の `question.list` で取り直し、`question.requested` / `question.resolved` で追う。
 * 答えるのは利用者の明示の操作だけ（`question.resolve`）。シートを閉じた質問は [snoozed] に入れ、会話の入力欄の上から開き直せる。
 */
internal class QuestionsRepository(private val gateway: GatewayClient, scope: CoroutineScope) {
    private val pending = MutableStateFlow<List<PendingQuestion>>(emptyList())
    val requests = pending.asStateFlow()

    private val _snoozed = MutableStateFlow<Set<String>>(emptySet())
    /** シートを閉じて「あとで」にした質問の id。 */
    val snoozed = _snoozed.asStateFlow()

    private var connection: ConnectionState.Connected? = null
    // 取り直しの最中に届いた通知を、古い一覧で上書きしない
    private var revision = 0

    init {
        scope.launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
            gateway.events.collect { event ->
                if (connection == null || gateway.state.value !== connection) return@collect
                when (event.name) {
                    "question.requested" -> {
                        revision++
                        val item = parsePendingQuestion(event.payload) ?: return@collect
                        replace(pending.value.filterNot { it.id == item.id } + item)
                    }
                    "question.resolved" -> {
                        revision++
                        val id = (event.payload as? JsonObject)?.string("id") ?: return@collect
                        replace(pending.value.filterNot { it.id == id })
                    }
                }
            }
        }
        scope.launch(Dispatchers.Main.immediate) {
            gateway.state.collectLatest { state ->
                connection = (state as? ConnectionState.Connected)?.takeIf { "question.list" in it.methods }
                replace(emptyList())
                val active = connection ?: return@collectLatest
                replay(active)
            }
        }
        // 期限が来た質問を外す
        scope.launch(Dispatchers.Main.immediate) {
            pending.collectLatest { list ->
                val expires = list.minOfOrNull { it.expiresAt } ?: return@collectLatest
                delay((expires - System.currentTimeMillis()).coerceAtLeast(0))
                replace(pending.value)
            }
        }
    }

    private fun replace(items: List<PendingQuestion>) {
        val now = System.currentTimeMillis()
        val live = items.filter { it.expiresAt > now }.sortedBy { it.createdAt }
        pending.value = live
        _snoozed.update { ids -> ids.filterTo(mutableSetOf()) { id -> live.any { it.id == id } } }
    }

    private suspend fun replay(active: ConnectionState.Connected) {
        repeat(3) {
            val snapshot = revision
            val rows = try {
                gateway.request("question.list", buildJsonObject {}, timeoutMs = 5_000).asObject()?.array("questions") ?: return
            } catch (_: TimeoutCancellationException) {
                return
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                return
            }
            if (gateway.state.value !== active) return
            if (snapshot == revision) {
                replace(rows.mapNotNull { parsePendingQuestion(it) }.distinctBy { it.id })
                return
            }
        }
    }

    fun snooze(id: String) = _snoozed.update { it + id }
    fun reopen(id: String) = _snoozed.update { it - id }

    /** 答えを送る。`answers` は questionId → 選んだ値（自由入力を含む）。 */
    suspend fun answer(item: PendingQuestion, answers: Map<String, List<String>>) = resolve(item) {
        putJsonObject("answers") {
            putJsonObject("answers") {
                answers.forEach { (questionId, values) -> put(questionId, buildJsonArray { values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }) }
            }
        }
    }

    /** スキップ（質問全体を断る）。 */
    suspend fun skip(item: PendingQuestion) = resolve(item) { put("cancel", true) }

    private suspend fun resolve(item: PendingQuestion, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        val active = checkNotNull(connection)
        check(gateway.state.value === active && "question.resolve" in active.methods)
        check(pending.value.any { it.id == item.id } && item.expiresAt > System.currentTimeMillis())
        gateway.request("question.resolve", buildJsonObject {
            put("id", item.id)
            body()
        })
        if (gateway.state.value === active) {
            revision++
            replace(pending.value.filterNot { it.id == item.id })
        }
    }
}
