package jp.miyavi.android.clawdroid.core.gateway

import java.net.URI

/** 接続先の検証の結果。 */
sealed interface UrlCheck {
    data class Ok(val normalized: String) : UrlCheck
    data object Invalid : UrlCheck
    /** 暗号化しない ws:// をプライベートでないアドレスに使おうとした。 */
    data object CleartextPublic : UrlCheck
}

object GatewayUrl {
    /**
     * `ws://` はプライベートアドレス（LAN・Tailscale）だけに許す（data-model 1、憲章 IV）。
     * `http(s)://` で入力されたら `ws(s)://` に読み替える。
     */
    fun check(input: String): UrlCheck {
        val trimmed = input.trim().let {
            when {
                it.startsWith("https://") -> "wss://" + it.removePrefix("https://")
                it.startsWith("http://") -> "ws://" + it.removePrefix("http://")
                else -> it
            }
        }
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return UrlCheck.Invalid
        val host = uri.host?.lowercase() ?: return UrlCheck.Invalid
        return when (uri.scheme?.lowercase()) {
            "wss" -> UrlCheck.Ok(trimmed)
            "ws" -> if (isPrivateHost(host)) UrlCheck.Ok(trimmed) else UrlCheck.CleartextPublic
            else -> UrlCheck.Invalid
        }
    }

    /** Tailscale の経路らしいアドレスか（100.64.0.0/10・MagicDNS の .ts.net）。案内に使うだけで、信頼の判断には使わない。 */
    fun isTailscaleHost(host: String): Boolean {
        if (host.endsWith(".ts.net")) return true
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        return octets.size == 4 && octets[0] == 100 && octets[1] in 64..127
    }

    /** URL からホスト名だけを取り出す（表示と案内用）。 */
    fun hostOf(url: String): String? = runCatching { java.net.URI(url.trim()).host }.getOrNull()?.removeSurrounding("[", "]")

    fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".ts.net") || host.endsWith(".lan")) return true
        // 点の無いホスト名（LAN の名前解決）
        if (!host.contains('.') && !host.contains(':')) return true
        val literal = host.removeSurrounding("[", "]")
        if (':' in literal && literal.matches(Regex("[0-9a-fA-F:.]+"))) {
            return runCatching { isPrivateAddress(java.net.InetAddress.getByName(literal)) }.getOrDefault(false)
        }
        val parts = literal.split('.')
        if (parts.size != 4 || parts.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) }) return false
        val numbers = parts.map { it.toInt() }
        return numbers.all { it <= 255 } && isPrivateAddress(java.net.InetAddress.getByAddress(numbers.map { it.toByte() }.toByteArray()))
    }

    internal fun isPrivateAddress(address: java.net.InetAddress): Boolean {
        val bytes = address.address
        return address.isLoopbackAddress || address.isLinkLocalAddress ||
            (bytes.size == 4 && (address.isSiteLocalAddress || (bytes[0].toInt() == 100 && bytes[1].toInt() in 64..127))) ||
            (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc)
    }
}
