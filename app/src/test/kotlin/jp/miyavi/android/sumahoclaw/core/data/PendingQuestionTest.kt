package jp.miyavi.android.sumahoclaw.core.data

import jp.miyavi.android.sumahoclaw.core.gateway.Frames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingQuestionTest {
    private fun parse(json: String) = parsePendingQuestion(Frames.json.parseToJsonElement(json))

    @Test fun parsesAskUserRecord() {
        val q = parse(
            """{"id":"q1","status":"pending","createdAtMs":1,"expiresAtMs":2,"agentId":"a","sessionKey":"agent:a:s",
               "questions":[{"questionId":"terminal_owner","header":"所有","question":"どうする？","multiSelect":false,
                 "options":[{"label":"B (Recommended)","description":"接続だけ"},{"label":"A","value":"session"}]}]}""",
        )!!
        assertEquals("q1", q.id)
        assertTrue(q.answerable)
        assertTrue(q.belongsTo("agent:a:s"))
        assertFalse(q.belongsTo("agent:a:other"))
        val item = q.questions.single()
        assertEquals(listOf("B (Recommended)", "session"), item.options.map { it.value })
        assertEquals("接続だけ", item.options.first().description)
    }

    @Test fun secretOrResolved_areNotAnswerableHere() {
        val secret = parse("""{"id":"q2","createdAtMs":1,"expiresAtMs":2,"questions":[{"questionId":"k","header":"","question":"key","options":[],"isSecret":true}]}""")!!
        assertFalse(secret.answerable)
        assertTrue(secret.belongsTo("anything"))
        assertNull(parse("""{"id":"q3","status":"answered","createdAtMs":1,"expiresAtMs":2,"questions":[{"questionId":"k","header":"","question":"x","options":[]}]}"""))
    }
}
