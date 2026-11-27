package jp.miyavi.android.sumahoclaw.core.data

import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * リンクの見出し（ページのタイトルとファビコン）。[title] は取れなければ null（ドメインを出す）。
 * [icon] はファビコンの中身（取れなければ null。画面は地球のアイコンを出す）。
 */
class LinkPreview(val title: String?, val icon: ByteArray?)

/**
 * 会話のリンクのタイトルとファビコンを、端末からリンク先のページを読んで取る（送ったあとに、アプリ側で。マスター指定 2026-11-26）。
 * 読むのは先頭の一部（[MAX_HTML_BYTES]）だけ。結果は手元に覚え、同じリンクを何度も読まない。
 *
 * 家の中や端末の中にしかない宛先（プライベート・ループバック・リンクローカル・CGNAT・ULA など）には接続しない。
 * ページ・転送先・ファビコンのどれも同じ決まりで、名前を引いた結果と実際につないだ先の両方で確かめる（CJ レビュー 2026-11-26）。
 */
class LinkPreviewLoader(client: OkHttpClient) {
    private val client = client.newBuilder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // 名前を引いた結果に内側の宛先が混じっていたら、つなぐ前に断る
        .dns(PublicOnlyDns)
        // プロキシは使わない（プロキシが選ばれる宛先は、そもそも取りに行かない。isDirect）
        .proxy(java.net.Proxy.NO_PROXY)
        // 転送は 1 回ずつ自分で確かめて追う
        .followRedirects(false)
        .followSslRedirects(false)
        // 実際につないだ先も確かめる（数字で書いた宛先や、名前の引き直しで変わったときのため）
        .addNetworkInterceptor { chain ->
            val address = chain.connection()?.route()?.socketAddress?.address
            if (address == null || !isPublicAddress(address)) throw java.io.IOException("blocked non-public address")
            chain.proceed(chain.request())
        }
        .build()
    private val cache = LruCache<String, LinkPreview>(CACHE_ENTRIES)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** すでに取ってあれば、その値（画面の最初の組み立てで使う）。 */
    fun cached(url: String): LinkPreview? = cache.get(url)

    suspend fun load(url: String): LinkPreview? {
        cache.get(url)?.let { return it }
        val http = url.toHttpUrlOrNull()?.takeIf { it.isWeb() && isPublicHost(it.host) && isDirect(it) } ?: return null
        return locks.getOrPut(url) { Mutex() }.withLock {
            cache.get(url)?.let { return@withLock it }
            val preview = fetch(http)
            cache.put(url, preview)
            preview
        }
    }

    private suspend fun fetch(url: HttpUrl): LinkPreview = withContext(Dispatchers.IO) {
        var title: String? = null
        var iconUrl: HttpUrl = url.faviconUrl()
        try {
            get(url, "text/html,application/xhtml+xml") { response ->
                val body = response.body
                val type = body.contentType()
                val base = response.request.url
                iconUrl = base.faviconUrl()
                if (!response.isSuccessful || (type != null && type.subtype !in HTML_TYPES)) return@get
                val bytes = body.byteStream().readNBytes(MAX_HTML_BYTES)
                val head = String(bytes, Charsets.ISO_8859_1)
                val charset = type?.charset() ?: META_CHARSET.find(head)?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
                val html = String(bytes, charset)
                title = pageTitle(html)
                pageIcon(html, base)?.toHttpUrlOrNull()?.takeIf { it.isWeb() }?.let { iconUrl = it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        // ファビコンも同じ決まりの接続で取り、中身を画面に渡す（画像の読み込みの仕組みに直接取りに行かせない）
        val icon = try {
            var bytes: ByteArray? = null
            if (isPublicHost(iconUrl.host) && isDirect(iconUrl)) get(iconUrl, "image/*") { response ->
                if (response.isSuccessful) bytes = response.body.byteStream().readNBytes(MAX_ICON_BYTES + 1).takeIf { it.size <= MAX_ICON_BYTES }
            }
            bytes
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        LinkPreview(title, icon)
    }

    /** [url] を取り、転送は宛先を確かめながら [MAX_REDIRECTS] 回まで追う。最後の応答を [read] に渡す。 */
    private fun get(url: HttpUrl, accept: String, read: (okhttp3.Response) -> Unit) {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val request = Request.Builder().url(current).header("Accept", accept).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                val next = response.header("Location")?.takeIf { response.isRedirect }?.let { current.resolve(it) }
                if (next == null) {
                    read(response)
                    return
                }
                if (!next.isWeb() || !isPublicHost(next.host) || !isDirect(next)) return
                current = next
            }
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 8L
        const val MAX_HTML_BYTES = 256 * 1024
        const val MAX_ICON_BYTES = 64 * 1024
        const val MAX_REDIRECTS = 5
        const val CACHE_ENTRIES = 256
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) SumahoClaw"
        val HTML_TYPES = setOf("html", "xhtml+xml")
        val META_CHARSET = Regex("<meta[^>]+charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
    }
}

private fun HttpUrl.isWeb() = scheme == "http" || scheme == "https"

/**
 * 端末のプロキシを通らずに直接つながる宛先か。プロキシを通ると宛先の確認ができず、プロキシを外して直結すると
 * 管理されたネットワークの決まりを迂回しかねないので、プロキシが選ばれる宛先は見出しを取らずドメインだけを出す（CJ レビュー 2026-11-26）。
 */
private fun isDirect(url: HttpUrl): Boolean = runCatching {
    val proxies = java.net.ProxySelector.getDefault()?.select(url.toUri()).orEmpty()
    proxies.all { it.type() == java.net.Proxy.Type.DIRECT }
}.getOrDefault(false)

private fun HttpUrl.faviconUrl(): HttpUrl = newBuilder().encodedPath("/favicon.ico").query(null).fragment(null).build()

/** 名前を引いて、内側の宛先が 1 つでも混じっていたら断る。 */
private object PublicOnlyDns : okhttp3.Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> {
        val addresses = okhttp3.Dns.SYSTEM.lookup(hostname)
        if (addresses.any { !isPublicAddress(it) }) throw java.net.UnknownHostException("non-public address for $hostname")
        return addresses
    }
}

/** 名前の見た目だけで分かる内側の宛先（数字で書いた宛先・localhost・.local など）。 */
internal fun isPublicHost(host: String): Boolean {
    val h = host.lowercase().trimEnd('.')
    if (h == "localhost" || INTERNAL_SUFFIXES.any { h.endsWith(it) }) return false
    val literal = ':' in h || IPV4_LITERAL.matches(h)
    if (!literal) return true
    return runCatching { isPublicAddress(java.net.InetAddress.getByName(h)) }.getOrDefault(false)
}

/** インターネットの向こうにある宛先か（プライベート・ループバック・リンクローカル・CGNAT・ULA・マルチキャストでない）。 */
internal fun isPublicAddress(address: java.net.InetAddress): Boolean {
    if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
        address.isAnyLocalAddress || address.isMulticastAddress
    ) return false
    val b = address.address
    return when (b.size) {
        // 100.64.0.0/10（CGNAT。Tailscale もここ）、0.0.0.0/8
        4 -> !((b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 64) && b[0].toInt() != 0
        // fc00::/7（ULA）
        16 -> (b[0].toInt() and 0xFE) != 0xFC
        else -> false
    }
}

private val IPV4_LITERAL = Regex("^[0-9.]+$")
private val INTERNAL_SUFFIXES = listOf(".local", ".localhost", ".internal", ".lan", ".home", ".home.arpa", ".ts.net")

/** og:title → twitter:title → <title> の順。空白はまとめ、HTML の文字参照を戻す。 */
internal fun pageTitle(html: String): String? {
    val candidates = listOf(metaContent(html, "og:title"), metaContent(html, "twitter:title"), TITLE.find(html)?.groupValues?.get(1))
    return candidates.firstNotNullOfOrNull { raw ->
        raw?.let(::unescapeHtml)?.replace(SPACES, " ")?.trim()?.takeIf(String::isNotEmpty)
    }
}

/** `<link rel="icon">` などのファビコン（相対なら [base] から解く）。無ければ null。 */
internal fun pageIcon(html: String, base: HttpUrl): String? =
    LINK_TAG.findAll(html).mapNotNull { tag ->
        val attrs = tag.value
        val rel = attr(attrs, "rel")?.lowercase() ?: return@mapNotNull null
        if ("icon" !in rel.split(Regex("\\s+"))) return@mapNotNull null
        attr(attrs, "href")?.let { base.resolve(unescapeHtml(it))?.toString() }
    }.firstOrNull()

private fun metaContent(html: String, key: String): String? = META_TAG.findAll(html).firstNotNullOfOrNull { tag ->
    val attrs = tag.value
    val name = attr(attrs, "property") ?: attr(attrs, "name")
    if (name.equals(key, ignoreCase = true)) attr(attrs, "content") else null
}

private fun attr(tag: String, name: String): String? =
    Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(tag)?.let { m ->
        m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
    }

/** よく使う文字参照だけを戻す（名前付きの主なものと、数値の参照）。 */
private fun unescapeHtml(text: String): String = ENTITY.replace(text) { m ->
    val body = m.groupValues[1]
    when {
        body.startsWith("#x", ignoreCase = true) -> body.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        body.startsWith("#") -> body.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else -> NAMED_ENTITIES[body.lowercase()]
    } ?: m.value
}

private val ENTITY = Regex("&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);")
private val NAMED_ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "hellip" to "…", "mdash" to "—", "ndash" to "–")

private val TITLE = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
private val SPACES = Regex("\\s+")

/** 本文の中の Web のリンク（ASCII の範囲だけ。末尾の句読点・閉じかっこは含めない）。 */
private val URL_IN_TEXT = Regex("https?://[!#-;=?-~&&[^<>\"']]+")
private const val TRAILING_PUNCTUATION = ".,;:!?)]}、。，．！？"

/** 本文の中の Web のリンクの位置。 */
fun findWebLinks(text: String): List<IntRange> = URL_IN_TEXT.findAll(text).map { m ->
    var end = m.range.last
    while (end > m.range.first && text[end] in TRAILING_PUNCTUATION) end--
    m.range.first..end
}.toList()
