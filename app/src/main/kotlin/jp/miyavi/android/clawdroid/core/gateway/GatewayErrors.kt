package jp.miyavi.android.clawdroid.core.gateway

/** 接続に失敗した理由。利用者に区別して示す（FR-004）。 */
enum class FailureReason {
    /** トークンの拒否。自動の再接続はしない。 */
    AUTH_REJECTED,
    /** 端末の承認が拒否・期限切れ。自動の再接続はしない。 */
    PAIRING_REJECTED,
    /** 到達できない（段階的に延ばしながら再接続する）。 */
    UNREACHABLE,
    /** プロトコルの版が合わない。 */
    PROTOCOL_MISMATCH,
    OTHER,
}

/** RPC の失敗。 */
class GatewayRpcException(val error: GatewayError) : Exception("${error.code}: ${error.message}") {
    val missingScope: Boolean get() = error.detailCode == "MISSING_SCOPE" || error.message.contains("missing scope", ignoreCase = true)
}

/** 接続が無い・切れたときの失敗。 */
class GatewayNotConnectedException : Exception("gateway not connected")

internal object GatewayErrorClassifier {
    private val authCodes = setOf(
        "AUTH_REQUIRED", "AUTH_UNAUTHORIZED", "AUTH_TOKEN_MISSING", "AUTH_TOKEN_MISMATCH",
        "AUTH_TOKEN_NOT_CONFIGURED", "AUTH_PASSWORD_MISSING", "AUTH_PASSWORD_MISMATCH",
        "AUTH_DEVICE_TOKEN_MISMATCH", "AUTH_SCOPE_MISMATCH", "AUTH_RATE_LIMITED",
        "DEVICE_IDENTITY_REQUIRED", "DEVICE_AUTH_INVALID", "DEVICE_AUTH_DEVICE_ID_MISMATCH",
        "DEVICE_AUTH_SIGNATURE_INVALID", "DEVICE_AUTH_PUBLIC_KEY_INVALID", "OPERATOR_ACCESS_DENIED",
    )

    fun isPairingRequired(e: GatewayError): Boolean =
        e.detailCode == "PAIRING_REQUIRED" || e.code == "NOT_PAIRED"

    fun waitsForResolution(e: GatewayError): Boolean = e.details?.bool("waitForResolution") == true

    fun classify(e: GatewayError): FailureReason {
        val code = e.detailCode ?: e.code
        return when {
            code == "PAIRING_REJECTED" || code == "PAIRING_EXPIRED" -> FailureReason.PAIRING_REJECTED
            code == "PROTOCOL_MISMATCH" || code == "CLIENT_VERSION_MISMATCH" -> FailureReason.PROTOCOL_MISMATCH
            code in authCodes || e.code.startsWith("AUTH") -> FailureReason.AUTH_REJECTED
            else -> FailureReason.OTHER
        }
    }

    /** 自動で再接続してよい失敗か（AUTH_RATE_LIMITED などは待てば通る）。 */
    fun retryable(e: GatewayError): Boolean {
        val step = e.details?.string("recommendedNextStep")
        return e.retryable == true || step == "wait_then_retry" || e.detailCode == "AUTH_RATE_LIMITED"
    }
}
