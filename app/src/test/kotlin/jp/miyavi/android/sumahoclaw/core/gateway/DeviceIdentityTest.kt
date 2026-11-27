package jp.miyavi.android.sumahoclaw.core.gateway

import jp.miyavi.android.sumahoclaw.testing.InMemorySecretStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityTest {
    @Test fun v3Payload_matchesOfficialLayout() {
        val payload = DeviceAuthPayload.buildV3(
            deviceId = "dev", clientId = "openclaw-android", clientMode = "ui", role = "operator",
            scopes = listOf("operator.read", "operator.write"), signedAtMs = 123, token = "tok",
            nonce = "n1", platform = "Android", deviceFamily = " Android ",
        )
        assertEquals("v3|dev|openclaw-android|ui|operator|operator.read,operator.write|123|tok|n1|android|android", payload)
    }

    @Test fun v3Payload_emptyTokenStaysEmpty() {
        val payload = DeviceAuthPayload.buildV3("d", "c", "ui", "operator", emptyList(), 1, null, "n", null, null)
        assertEquals("v3|d|c|ui|operator||1||n||", payload)
    }

    @Test fun signature_verifiesAndDetectsTampering() {
        val identity = DeviceIdentity.generate()
        val signature = identity.sign("hello")
        assertTrue(identity.verify("hello", signature))
        assertFalse(identity.verify("hellO", signature))
        assertEquals(64, identity.deviceId.length)
    }

    @Test fun repository_createsOnceAndReloadsSameId() = runTest {
        val store = InMemorySecretStore()
        val first = DeviceIdentityRepository(store).loadOrCreate()
        val second = DeviceIdentityRepository(store).loadOrCreate()
        assertEquals(first.deviceId, second.deviceId)
        assertEquals(first.publicKeyBase64Url, second.publicKeyBase64Url)
    }
}
