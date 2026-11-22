package jp.miyavi.android.clawdroid.feature.chat

import jp.miyavi.android.clawdroid.core.data.ChatMessage
import jp.miyavi.android.clawdroid.core.data.ModelChange
import jp.miyavi.android.clawdroid.core.data.Parse
import jp.miyavi.android.clawdroid.core.gateway.Frames
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelChangeTest {
    private fun message(json: String, index: Int = 0): ChatMessage =
        Parse.message(Frames.json.parseToJsonElement(json).jsonObject, index)!!

    private fun user(provider: String, model: String, previous: String? = null, index: Int = 0) = message(
        """{"role":"user","content":"hi","__openclaw":{"id":"u$index","modelSelection":{"provider":"$provider","model":"$model"${previous?.let { p -> ""","previous":{"provider":"${p.substringBefore('/')}","model":"${p.substringAfter('/')}"}""" } ?: ""}}}}""",
        index,
    )

    private val assistant = message("""{"role":"assistant","content":"ok"}""")

    private fun changes(vararg messages: ChatMessage) = formatMessages(messages.toList()) { "" }.map { it.modelChange }

    @Test fun parse_readsSelectionAndCustomChange() {
        val u = user("openai", "gpt-6.2-sol", previous = "anthropic/claude-opus-5-5")
        assertEquals("openai/gpt-6.2-sol", u.modelSelection?.current)
        assertEquals("anthropic/claude-opus-5-5", u.modelSelection?.previous)
        val custom = message("""{"role":"custom","customType":"session-model-change","details":{"from":"a/x","to":"b/y"}}""")
        assertEquals(ModelChange("a/x", "b/y"), custom.modelChange)
    }

    @Test fun divider_onlyWhereTheModelChanges() {
        val result = changes(
            user("anthropic", "claude-opus-5-5", index = 1), assistant,
            user("anthropic", "claude-opus-5-5", index = 2), assistant,
            user("openai", "gpt-6.2-sol", index = 3), assistant,
        )
        assertEquals(listOf(null, null, null, null, ModelChange("anthropic/claude-opus-5-5", "openai/gpt-6.2-sol"), null), result)
    }

    @Test fun firstSelection_usesRecordedPreviousOnlyAfterEarlierTurns() {
        // 履歴の先頭では直前の選択を比べない（Control UI と同じ）
        assertNull(changes(user("openai", "gpt-6.2-sol", previous = "anthropic/claude-opus-5-5"))[0])
        // それより前の会話があれば、記録の直前の選択と比べる
        assertEquals(
            ModelChange("anthropic/claude-opus-5-5", "openai/gpt-6.2-sol"),
            changes(assistant, user("openai", "gpt-6.2-sol", previous = "anthropic/claude-opus-5-5"))[1],
        )
    }

    @Test fun customChange_isNotRepeatedByTheNextMessage() {
        val custom = message("""{"role":"custom","customType":"session-model-change","details":{"from":"anthropic/claude-opus-5-5","to":"openai/gpt-6.2-sol"}}""")
        val result = changes(user("anthropic", "claude-opus-5-5", index = 1), custom, user("openai", "gpt-6.2-sol", index = 2))
        assertEquals(listOf(null, ModelChange("anthropic/claude-opus-5-5", "openai/gpt-6.2-sol"), null), result)
    }
}
