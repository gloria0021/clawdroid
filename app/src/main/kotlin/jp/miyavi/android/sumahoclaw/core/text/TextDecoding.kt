package jp.miyavi.android.sumahoclaw.core.text

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** エディターが扱う文字コード（spec 008 FR-040）。 */
enum class TextCharset(val label: String, private val names: List<String>) {
    UTF_8("UTF-8", listOf("UTF-8")),
    UTF_16LE("UTF-16LE", listOf("UTF-16LE")),
    UTF_16BE("UTF-16BE", listOf("UTF-16BE")),

    // Windows で作られたファイルの丸数字・機種依存文字を読むため、windows-31j（MS932）を先に試す
    SHIFT_JIS("Shift_JIS", listOf("windows-31j", "Shift_JIS")),
    EUC_JP("EUC-JP", listOf("EUC-JP"));

    /** この端末で使える Charset（無ければ null）。 */
    val charset: Charset? by lazy { names.firstNotNullOfOrNull { runCatching { Charset.forName(it) }.getOrNull() } }

    val bomBytes: ByteArray
        get() = when (this) {
            UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            else -> ByteArray(0)
        }
}

/** 改行の種類（FR-042）。[MIXED] は混ざっている。 */
enum class LineSeparator(val chars: String, val label: String) {
    LF("\n", "LF"),
    CRLF("\r\n", "CRLF"),
    CR("\r", "CR"),
    MIXED("\n", "Mixed"),
}

/**
 * 開いた文書の書式。本文は BOM を外し、改行を LF にそろえて持つ（Sora に渡す形）。
 * [dominant] は混在のときに多い方（保存するときにそろえる先）。
 */
data class DocumentFormat(
    val charset: TextCharset = TextCharset.UTF_8,
    val bom: Boolean = false,
    val lineSeparator: LineSeparator = LineSeparator.LF,
    val dominant: LineSeparator = if (lineSeparator == LineSeparator.MIXED) LineSeparator.LF else lineSeparator,
) {
    /** 保存するときに使う改行。 */
    val writeSeparator: LineSeparator get() = if (lineSeparator == LineSeparator.MIXED) dominant else lineSeparator
}

/** 読んだ結果。[text] は BOM を外し LF にそろえたもの。 */
data class DecodedText(val text: String, val format: DocumentFormat)

/** 書いた結果。 */
sealed interface EncodeResult {
    class Ok(val bytes: ByteArray) : EncodeResult
    /** 元の文字コードで書けない文字があった（[offset] は LF にそろえた本文の中の位置）。 */
    data class Unmappable(val offset: Int) : EncodeResult
}

/**
 * 文字コード・BOM・改行の判定と読み書き（spec 008 research R5）。対象は [TextCharset] の 5 種類だけ。
 */
object TextDecoding {

    /** 判定して読む。文字として読めなければ null（FR-044）。 */
    fun detect(bytes: ByteArray): DecodedText? {
        bomOf(bytes)?.let { charset ->
            val body = strict(bytes, charset.bomBytes.size, charset) ?: return null
            return decoded(body, charset, bom = true)
        }
        if (bytes.any { it == 0.toByte() }) return null
        strict(bytes, 0, TextCharset.UTF_8)?.let { return decoded(it, TextCharset.UTF_8, bom = false) }
        val sjis = strict(bytes, 0, TextCharset.SHIFT_JIS)
        val euc = strict(bytes, 0, TextCharset.EUC_JP)
        val chosen = when {
            sjis != null && euc != null -> if (halfwidthKanaRatio(euc) <= halfwidthKanaRatio(sjis)) TextCharset.EUC_JP to euc else TextCharset.SHIFT_JIS to sjis
            sjis != null -> TextCharset.SHIFT_JIS to sjis
            euc != null -> TextCharset.EUC_JP to euc
            else -> return null
        }
        return decoded(chosen.second, chosen.first, bom = false)
    }

    /** 利用者が選んだ文字コードで読み直す（読めない文字は置き換え文字にする）。 */
    fun decodeAs(bytes: ByteArray, charset: TextCharset): DecodedText {
        val bom = charset.bomBytes.isNotEmpty() && bytes.startsWith(charset.bomBytes)
        val start = if (bom) charset.bomBytes.size else 0
        val cs = charset.charset ?: Charsets.UTF_8
        val body = String(bytes, start, bytes.size - start, cs)
        return decoded(body, charset, bom)
    }

    /** Gateway から受け取った文字列（UTF-8 で読まれ、BOM は U+FEFF として残っている。research R4）。 */
    fun fromGatewayText(content: String): DecodedText {
        val bom = content.startsWith(BOM_CHAR)
        return decoded(if (bom) content.substring(1) else content, TextCharset.UTF_8, bom)
    }

    /** Gateway へ書き戻す文字列。BOM と改行を戻す。 */
    fun toGatewayText(text: String, format: DocumentFormat): String =
        (if (format.bom) BOM_CHAR else "") + withSeparator(text, format.writeSeparator)

    /** 書式どおりのバイトにする。書けない文字があれば [EncodeResult.Unmappable]。 */
    fun encode(text: String, format: DocumentFormat): EncodeResult {
        val cs = format.charset.charset ?: return EncodeResult.Unmappable(0)
        val encoder = cs.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        // 書けない文字の位置を、LF にそろえた本文の位置で返すため、改行を戻す前に確かめる
        if (!encoder.canEncode(text)) return EncodeResult.Unmappable(firstUnmappable(text, cs))
        val body = withSeparator(text, format.writeSeparator)
        val encoded = try {
            encoder.reset().encode(CharBuffer.wrap(body))
        } catch (_: CharacterCodingException) {
            return EncodeResult.Unmappable(firstUnmappable(text, cs))
        }
        val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        return EncodeResult.Ok(if (format.bom) format.charset.bomBytes + bytes else bytes)
    }

    /** 改行の種類を判定する。改行が無ければ LF。 */
    fun separatorOf(text: CharSequence): Pair<LineSeparator, LineSeparator> {
        var lf = 0
        var crlf = 0
        var cr = 0
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '\r' -> if (i + 1 < text.length && text[i + 1] == '\n') { crlf++; i++ } else cr++
                '\n' -> lf++
            }
            i++
        }
        val kinds = listOf(LineSeparator.LF to lf, LineSeparator.CRLF to crlf, LineSeparator.CR to cr).filter { it.second > 0 }
        val dominant = kinds.maxByOrNull { it.second }?.first ?: LineSeparator.LF
        return (if (kinds.size > 1) LineSeparator.MIXED else dominant) to dominant
    }

    /** 改行を LF にそろえる。 */
    fun normalize(text: String): String =
        if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

    private fun decoded(raw: String, charset: TextCharset, bom: Boolean): DecodedText {
        val (separator, dominant) = separatorOf(raw)
        return DecodedText(normalize(raw), DocumentFormat(charset, bom, separator, dominant))
    }

    private fun withSeparator(text: String, separator: LineSeparator): String =
        if (separator == LineSeparator.LF || separator == LineSeparator.MIXED) text else text.replace("\n", separator.chars)

    private fun bomOf(bytes: ByteArray): TextCharset? = listOf(TextCharset.UTF_8, TextCharset.UTF_16LE, TextCharset.UTF_16BE)
        .firstOrNull { bytes.startsWith(it.bomBytes) }

    private fun strict(bytes: ByteArray, offset: Int, charset: TextCharset): String? {
        val cs = charset.charset ?: return null
        return try {
            cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /** 半角カナ（U+FF61〜FF9F）の割合。EUC-JP を Shift_JIS で読むと半角カナだらけになる。 */
    private fun halfwidthKanaRatio(text: String): Double {
        if (text.isEmpty()) return 0.0
        val kana = text.count { it in '｡'..'ﾟ' }
        return kana.toDouble() / text.length
    }

    private fun firstUnmappable(text: String, cs: Charset): Int {
        val encoder = cs.newEncoder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val chars = String(Character.toChars(cp))
            if (!encoder.canEncode(chars)) return i
            i += chars.length
        }
        return 0
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        prefix.isNotEmpty() && size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val BOM_CHAR = "\uFEFF"
}
