package jp.miyavi.android.clawdroid.core.data

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

/** リンクの見出し（ページのタイトルとファビコン）。[title] は取れなければ null（ドメインを出す）。 */
data class LinkPreview(val title: String?, val icon: String)

/**
 * 会話のリンクのタイトルとファビコンを、端末からリンク先のページを読んで取る（送ったあとに、アプリ側で。マスター指定 2026-11-26）。
 * 読むのは先頭の一部（[MAX_HTML_BYTES]）だけ。結果は手元に覚え、同じリンクを何度も読まない。
 */
class LinkPreviewLoader(client: OkHttpClient) {
    private val client = client.newBuilder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val cache = LruCache<String, LinkPreview>(CACHE_ENTRIES)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** すでに取ってあれば、その値（画面の最初の組み立てで使う）。 */
    fun cached(url: String): LinkPreview? = cache.get(url)

    suspend fun load(url: String): LinkPreview? {
        cache.get(url)?.let { return it }
        val http = url.toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" } ?: return null
        return locks.getOrPut(url) { Mutex() }.withLock {
            cache.get(url)?.let { return@withLock it }
            val preview = fetch(http)
            cache.put(url, preview)
            preview
        }
    }

    private suspend fun fetch(url: HttpUrl): LinkPreview = withContext(Dispatchers.IO) {
        val fallbackIcon = url.newBuilder().encodedPath("/favicon.ico").query(null).fragment(null).build().toString()
        try {
            val request = Request.Builder().url(url).header("Accept", "text/html,application/xhtml+xml").header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                val body = response.body
                val type = body.contentType()
                if (!response.isSuccessful || (type != null && type.subtype !in HTML_TYPES)) return@use LinkPreview(null, fallbackIcon)
                val bytes = body.byteStream().readNBytes(MAX_HTML_BYTES)
                val head = String(bytes, Charsets.ISO_8859_1)
                val charset = type?.charset() ?: META_CHARSET.find(head)?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
                val html = String(bytes, charset)
                val base = response.request.url
                LinkPreview(pageTitle(html), pageIcon(html, base) ?: base.newBuilder().encodedPath("/favicon.ico").query(null).fragment(null).build().toString())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LinkPreview(null, fallbackIcon)
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 8L
        const val MAX_HTML_BYTES = 256 * 1024
        const val CACHE_ENTRIES = 256
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Clawdroid"
        val HTML_TYPES = setOf("html", "xhtml+xml")
        val META_CHARSET = Regex("<meta[^>]+charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
    }
}

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
