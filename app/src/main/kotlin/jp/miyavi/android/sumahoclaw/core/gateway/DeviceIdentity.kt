package jp.miyavi.android.sumahoclaw.core.gateway

import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.MessageDigest
import java.util.Base64
import jp.miyavi.android.sumahoclaw.core.security.SecretStore
import jp.miyavi.android.sumahoclaw.core.security.StoredDeviceIdentity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 端末の Ed25519 の識別（research R2）。公式 Android アプリの DeviceIdentityStore と同じ形式。 */
class DeviceIdentity internal constructor(
    val deviceId: String,
    private val publicKey: ByteArray,
    private val privateKey: ByteArray,
) {
    /** Gateway が求める、パディングなしの URL セーフな Base64。 */
    val publicKeyBase64Url: String get() = base64Url(publicKey)

    fun sign(payload: String): String = base64Url(Ed25519Sign(privateKey).sign(payload.toByteArray(Charsets.UTF_8)))

    fun verify(payload: String, signatureBase64Url: String): Boolean = runCatching {
        Ed25519Verify(publicKey).verify(Base64.getUrlDecoder().decode(signatureBase64Url), payload.toByteArray(Charsets.UTF_8))
        true
    }.getOrDefault(false)

    companion object {
        /** deviceId は公開鍵（生の 32 バイト）の SHA-256 の 16 進。 */
        fun deriveDeviceId(publicKey: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(publicKey).joinToString("") { "%02x".format(it) }

        fun generate(): DeviceIdentity {
            val pair = Ed25519Sign.KeyPair.newKeyPair()
            return DeviceIdentity(deriveDeviceId(pair.publicKey), pair.publicKey, pair.privateKey)
        }

        internal fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/** 識別を一度だけ作り、暗号化して保存する。 */
class DeviceIdentityRepository(private val secrets: SecretStore) {
    private val mutex = Mutex()
    private var cached: DeviceIdentity? = null

    suspend fun loadOrCreate(): DeviceIdentity = mutex.withLock {
        cached?.let { return it }
        val stored = secrets.loadDeviceIdentity()
        val identity = if (stored != null) {
            val pub = Base64.getDecoder().decode(stored.publicKeyBase64)
            val priv = Base64.getDecoder().decode(stored.privateKeyBase64)
            DeviceIdentity(DeviceIdentity.deriveDeviceId(pub), pub, priv)
        } else {
            val pair = Ed25519Sign.KeyPair.newKeyPair()
            secrets.saveDeviceIdentity(
                StoredDeviceIdentity(
                    deviceId = DeviceIdentity.deriveDeviceId(pair.publicKey),
                    publicKeyBase64 = Base64.getEncoder().encodeToString(pair.publicKey),
                    privateKeyBase64 = Base64.getEncoder().encodeToString(pair.privateKey),
                ),
            )
            DeviceIdentity(DeviceIdentity.deriveDeviceId(pair.publicKey), pair.publicKey, pair.privateKey)
        }
        cached = identity
        identity
    }
}

/** 署名する内容（v3）。公式の `DeviceAuthPayload.buildV3` と同じ並び。 */
object DeviceAuthPayload {
    fun buildV3(
        deviceId: String,
        clientId: String,
        clientMode: String,
        role: String,
        scopes: List<String>,
        signedAtMs: Long,
        token: String?,
        nonce: String,
        platform: String?,
        deviceFamily: String?,
    ): String = listOf(
        "v3",
        deviceId,
        clientId,
        clientMode,
        role,
        scopes.joinToString(","),
        signedAtMs.toString(),
        token.orEmpty(),
        nonce,
        normalizeMetadataField(platform),
        normalizeMetadataField(deviceFamily),
    ).joinToString("|")

    /** ASCII の A–Z だけを小文字にする（ロケールに依存させない）。 */
    fun normalizeMetadataField(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        val out = StringBuilder(trimmed.length)
        for (ch in trimmed) out.append(if (ch in 'A'..'Z') (ch.code + 32).toChar() else ch)
        return out.toString()
    }
}
