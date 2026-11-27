package jp.miyavi.android.sumahoclaw.feature.terminal

/**
 * ターミナルを新しく開いたときに手元で描く看板（spec 005 US3）。絵は本家 OpenClaw の CLI（`src/cli/claw-banner.ts`、MIT）と同じ。
 * 桁数に合わせて形を選び、どの行も折り返さない（FR-021）。シェルには送らない（FR-022）。
 */
enum class BannerShape { SIDE_BY_SIDE, STACKED, MASCOT_ONLY, TEXT_ONLY }

private val MASCOT = listOf(
    " •●●:.        .:●●•",
    ":●●●●:        :●●●●:",
    ".●●●●:.:•●●•:.:●●●●.",
    " .●●●: •●●●●• :●●●.",
    " ..:••●●●●●●●●••:..",
    ".::••••●●●●●●••••::.",
    " . .:  •●●●●•  :. .",
    "    .  :●●●●:  .",
    "      .●●●●●●.",
    "       :••••:",
)
private val WORDMARK = listOf(
    "█▀▀▀█ █▀▀▀█ █▀▀▀▀ █▄  █ █▀▀▀▀ █     █▀▀▀█ █   █",
    "█   █ █▀▀▀▀ █▀▀▀  █ ▀▄█ █     █     █▀▀▀█ █▄▀▄█",
    "▀▀▀▀▀ ▀     ▀▀▀▀▀ ▀   ▀ ▀▀▀▀▀ ▀▀▀▀▀ ▀   ▀ ▀   ▀",
)
private const val MASCOT_WIDTH = 20
private const val WORDMARK_WIDTH = 47
private const val GAP = 3
private const val WORDMARK_ROW_OFFSET = 3
private const val NAME = "OpenClaw"

fun bannerShape(columns: Int): BannerShape = when {
    columns >= MASCOT_WIDTH + GAP + WORDMARK_WIDTH -> BannerShape.SIDE_BY_SIDE
    columns >= WORDMARK_WIDTH -> BannerShape.STACKED
    columns >= MASCOT_WIDTH -> BannerShape.MASCOT_ONLY
    else -> BannerShape.TEXT_ONLY
}

/** 看板の 1 行（マスコットの部分と、その右・下に続く既定の色の部分）。 */
data class BannerLine(val mascot: String, val rest: String) {
    val text: String get() = mascot + rest
}

fun bannerLines(columns: Int): List<BannerLine> {
    fun pad(width: Int, inside: Int) = " ".repeat(((width - inside) / 2).coerceAtLeast(0))
    return when (bannerShape(columns)) {
        BannerShape.SIDE_BY_SIDE -> MASCOT.mapIndexed { row, m ->
            val word = WORDMARK.getOrNull(row - WORDMARK_ROW_OFFSET)
            if (word == null) BannerLine(m, "") else BannerLine(m.padEnd(MASCOT_WIDTH), " ".repeat(GAP) + word)
        }
        BannerShape.STACKED -> MASCOT.map { BannerLine(pad(WORDMARK_WIDTH, MASCOT_WIDTH) + it, "") } +
            BannerLine("", "") + WORDMARK.map { BannerLine("", it) }
        BannerShape.MASCOT_ONLY -> MASCOT.map { BannerLine(it, "") } + BannerLine("", "") + BannerLine("", pad(MASCOT_WIDTH, NAME.length) + NAME)
        BannerShape.TEXT_ONLY -> listOf(BannerLine("", NAME))
    }
}

/** 端末へ流す文字列。マスコットは [mascotArgb] の 24 ビット色、ロゴは既定の文字色。最後に空行を 1 つ。 */
fun bannerAnsi(columns: Int, mascotArgb: Int): String {
    val color = "\u001b[38;2;${(mascotArgb shr 16) and 0xFF};${(mascotArgb shr 8) and 0xFF};${mascotArgb and 0xFF}m"
    return buildString {
        bannerLines(columns).forEach { line ->
            if (line.mascot.isNotEmpty()) append(color).append(line.mascot).append("\u001b[0m")
            append(line.rest.trimEnd()).append("\r\n")
        }
        append("\r\n")
    }
}
