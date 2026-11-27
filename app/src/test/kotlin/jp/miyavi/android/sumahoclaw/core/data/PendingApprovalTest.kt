package jp.miyavi.android.sumahoclaw.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PendingApprovalTest {
    @Test fun honorsAdvertisedDecisionsAndShowsWarning() {
        val item = parsePendingApproval(ApprovalKind.EXEC, Json.parseToJsonElement("""
            {"id":"a1","createdAtMs":100,"expiresAtMs":200,"request":{
                "command":"git status","agentId":"main","sessionKey":"agent:main:main",
                "warningText":"Review required","allowedDecisions":["deny","allow-once","future"]}}
        """))
        assertNotNull(item)
        assertEquals("Review required", item?.warning)
        assertEquals("agent:main:main", item?.sessionKey)
        assertEquals(setOf(ApprovalDecision.DENY, ApprovalDecision.ONCE), item?.decisions)
    }

    @Test fun missingDecisionsNeverEnablePermanentApproval() {
        val item = parsePendingApproval(ApprovalKind.PLUGIN, Json.parseToJsonElement("""
            {"id":"p1","createdAtMs":100,"expiresAtMs":200,"request":{"title":"Review plugin action"}}
        """))
        assertEquals(setOf(ApprovalDecision.DENY, ApprovalDecision.ONCE), item?.decisions)
    }

    @Test fun invalidApprovalIsRejected() {
        assertNull(parsePendingApproval(ApprovalKind.EXEC, Json.parseToJsonElement("""
            {"id":"a1","createdAtMs":100,"expiresAtMs":99,"request":{"command":"git status"}}
        """)))
    }
}
