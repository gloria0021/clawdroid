package jp.miyavi.android.clawdroid.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.core.graphics.ColorUtils
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.rememberDynamicColorScheme
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.settings.AppFont
import jp.miyavi.android.clawdroid.core.settings.AppTheme
import jp.miyavi.android.clawdroid.core.settings.Appearance
import jp.miyavi.android.clawdroid.core.settings.SurfaceStyle
import jp.miyavi.android.clawdroid.core.settings.ThemeSettings

// 色の直書きはこのファイルだけ（AGENTS.md 2.2）。配色全体は Material の色生成に任せ、ここには種色だけを置く。

/** OpenClaw の Control UI の赤（`ui/src/styles/base.css` の --accent）。ライト / ダークで別の種色。 */
private val OpenClawSeedLight = Color(0xFFDC2626)
private val OpenClawSeedDark = Color(0xFFFF5C5C)

/** Gerbil の Suna（砂）の種色。OpenClaw の面の土台に使う（赤みのある灰色）。 */
private val SunaSeed = Color(0xFF605050)

/** モダン（モノクロ）の種色。Monochrome では色相・彩度は使われない。 */
private val ModernSeed = Color(0xFF808080)

/** カスタム色の初期値。 */
val DefaultCustomSeed = Color(0xFFFFFFFF)

/** 背景を primaryContainer へ寄せる割合（ライト / ダーク）。Gerbil と同じ。 */
private const val TINT_WEIGHT_LIGHT = 0.18f
private const val TINT_WEIGHT_DARK = 0.22f


/** OpenClaw の背景を無彩色の面へ寄せる割合（ライトだけ。0.5 で RGB(224,215,215) → 約 (240,235,235)）。 */
private const val OPENCLAW_BACKGROUND_LIFT = 0.5f

/** OpenClaw のメッセージの面で、赤を背景に溶かす割合（大きいほど淡い）。 */
private const val NUANCE_MIX_LIGHT = 0.72f
private const val NUANCE_MIX_DARK = 0.62f

/** 補助の色に使うコントラスト（0 = 標準、0.5 = 中、1 = 高）。 */
internal const val CUSTOM_SECONDARY_CONTRAST = 0.5

/** リンク・強調の色が背景に対して保つコントラスト比（WCAG AA の 4.5 に余裕を持たせる）。 */
internal const val LINK_MIN_CONTRAST = 4.6

/** 本文の下地で、背景に重ねる本文色の不透明度（AGENTS.md 2.2）。 */
private const val BODY_OVERLAY_LIGHT = 0.03f
private const val BODY_OVERLAY_DARK = 0.05f

/** このテーマが暗い配色か。 */
fun Appearance.isDark(systemDark: Boolean): Boolean = when (this) {
    Appearance.SYSTEM -> systemDark
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

/** ガラスの屈折とぼかしの強さ（各 0〜10）。共通部品（Glass.kt）が読む。 */
data class GlassLevels(
    val refraction: Int = ThemeSettings.DEFAULT_GLASS_REFRACTION,
    val blurStrength: Int = ThemeSettings.DEFAULT_BLUR_STRENGTH,
)

val LocalGlassLevels = staticCompositionLocalOf { GlassLevels() }

/** 現在の透かし方（ガラス / ぼかし）。共通部品（Glass.kt）が読む。 */
val LocalSurfaceStyle = staticCompositionLocalOf { SurfaceStyle.GLASS }

private val SystemTypography = Typography()

private val LineSeedFamily = FontFamily(
    Font(R.font.line_seed_jp_regular, FontWeight.Normal),
    Font(R.font.line_seed_jp_bold, FontWeight.Bold),
)
private fun appTypography(font: AppFont, fontSizeSp: Int): Typography {
    val t = SystemTypography
    val scale = fontSizeSp / t.bodyLarge.fontSize.value
    val family = if (font == AppFont.LINE_SEED_JP) LineSeedFamily else null
    fun TextStyle.adjust() = copy(
        fontFamily = family ?: fontFamily,
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
    return t.copy(
        displayLarge = t.displayLarge.adjust(),
        displayMedium = t.displayMedium.adjust(),
        displaySmall = t.displaySmall.adjust(),
        headlineLarge = t.headlineLarge.adjust(),
        headlineMedium = t.headlineMedium.adjust(),
        headlineSmall = t.headlineSmall.adjust(),
        titleLarge = t.titleLarge.adjust(),
        titleMedium = t.titleMedium.adjust(),
        titleSmall = t.titleSmall.adjust(),
        bodyLarge = t.bodyLarge.adjust(),
        bodyMedium = t.bodyMedium.adjust(),
        bodySmall = t.bodySmall.adjust(),
        labelLarge = t.labelLarge.adjust(),
        labelMedium = t.labelMedium.adjust(),
        labelSmall = t.labelSmall.adjust(),
    )
}

@Composable
fun ClawdroidTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val dark = settings.appearance.isDark(isSystemInDarkTheme())
    val base = baseScheme(settings.theme, settings.customSeedArgb, dark)
    // モダンは面を黒へ寄せず、明るい無彩色のまま（マスター指定 2026-11-23: 濃すぎたので薄く）
    val tinted = remember(base, dark, settings.theme) {
        if (settings.theme == AppTheme.MODERN) softModernScheme(base, dark)
        // カスタムは種色のくすみ（ニュアンス）を primary にも残す（マスター指定 2026-11-24）
        else tintedScheme(base, dark, keepChroma = settings.theme == AppTheme.CUSTOM)
    }
    // OpenClaw: 面は Suna と同じ塗り方（赤みの落ち着いたオフホワイト）、赤はアクセントと利用者のメッセージの面だけ
    val red = if (settings.theme == AppTheme.OPENCLAW) {
        rememberDynamicColorScheme(seedColor = if (dark) OpenClawSeedDark else OpenClawSeedLight, isDark = dark, style = PaletteStyle.Fidelity)
    } else null
    val scheme = remember(tinted, red, dark) { if (red == null) tinted else withRedAccent(tinted, red, dark) }
    // OpenClaw の強い色の面は、送信ボタンの赤（primary）を背景に溶かしたくすみ色（ニュアンスカラー）。モダンも同じ塗り方の灰色
    val accent = if (red != null) {
        AccentContainer(lerp(scheme.primary, scheme.surface, if (dark) NUANCE_MIX_DARK else NUANCE_MIX_LIGHT), scheme.onSurface)
    } else if (settings.theme == AppTheme.MODERN) {
        AccentContainer(lerp(scheme.primary, scheme.surface, if (dark) MODERN_ACCENT_MIX_DARK else MODERN_ACCENT_MIX_LIGHT), scheme.onSurface)
    } else {
        AccentContainer(scheme.primaryContainer, scheme.onPrimaryContainer)
    }
    val typography = remember(settings.font, settings.fontSizeSp) { appTypography(settings.font, settings.fontSizeSp) }
    CompositionLocalProvider(
        LocalSurfaceStyle provides settings.surfaceStyle,
        LocalGlassLevels provides GlassLevels(settings.glassRefraction, settings.blurStrength),
        LocalAccentContainer provides accent,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            motionScheme = AppMotionScheme,
            typography = typography,
            content = content,
        )
    }
}

@Composable
private fun baseScheme(theme: AppTheme, customSeedArgb: Int?, dark: Boolean): ColorScheme = when (theme) {
    AppTheme.DYNAMIC -> {
        val context = LocalContext.current
        // 最小 SDK 33 なので Dynamic Color は常に使える
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    // 面の土台は Gerbil の Suna（赤みのある灰色の種色を Fidelity で）。赤は ClawdroidTheme で重ねる
    AppTheme.OPENCLAW -> {
        val base = rememberDynamicColorScheme(seedColor = SunaSeed, isDark = dark, style = PaletteStyle.Fidelity)
        val contrasted = rememberDynamicColorScheme(
            seedColor = SunaSeed, isDark = dark, style = PaletteStyle.Fidelity, contrastLevel = CUSTOM_SECONDARY_CONTRAST,
        )
        remember(base, contrasted) { withContrastedSecondary(base, contrasted) }
    }
    AppTheme.MODERN -> {
        val mono = rememberDynamicColorScheme(seedColor = ModernSeed, isDark = dark, style = PaletteStyle.Monochrome)
        remember(mono, dark) { withStandardError(mono, dark) }
    }
    AppTheme.CUSTOM -> {
        val seed = customSeedArgb?.let(::Color) ?: DefaultCustomSeed
        val base = rememberDynamicColorScheme(seedColor = seed, isDark = dark, style = PaletteStyle.Fidelity)
        val contrasted = rememberDynamicColorScheme(
            seedColor = seed, isDark = dark, style = PaletteStyle.Fidelity, contrastLevel = CUSTOM_SECONDARY_CONTRAST,
        )
        remember(base, contrasted) { withContrastedSecondary(base, contrasted) }
    }
}

/**
 * 背景を primaryContainer へ少し寄せ、リンクの色を背景に対して十分なコントラストにする（Gerbil と同じ処理）。
 */
internal fun tintedScheme(base: ColorScheme, dark: Boolean, keepChroma: Boolean = false): ColorScheme {
    val weight = if (dark) TINT_WEIGHT_DARK else TINT_WEIGHT_LIGHT
    val background = lerp(base.surface, base.primaryContainer, weight)
    return base.copy(
        surface = background,
        background = background,
        surfaceContainerLow = lerp(base.surfaceContainerLow, base.primaryContainer, weight * 0.75f),
        surfaceContainer = lerp(base.surfaceContainer, base.primaryContainer, weight * 0.75f),
        surfaceContainerHigh = lerp(base.surfaceContainerHigh, base.primaryContainer, weight * 0.6f),
        primary = vividLinkColor(base.primary, background, dark, keepChroma),
    )
}

/**
 * OpenClaw の赤を重ねる。面（背景・コンテナ・タイトルバーの色）は Suna の塗り方のまま、
 * アクセント（primary）だけを赤にする。利用者のメッセージの赤い面は [AccentContainer] で別に渡す。
 */
internal fun withRedAccent(surfaces: ColorScheme, red: ColorScheme, dark: Boolean): ColorScheme {
    // 背景は Suna より明るく: 背景と無彩色の面（ライトはほぼ白）の中間
    val background = if (dark) surfaces.surface else lerp(surfaces.surface, surfaces.surfaceContainerLowest, OPENCLAW_BACKGROUND_LIFT)
    return surfaces.copy(
    surface = background,
    background = background,
    primary = vividLinkColor(red.primary, background, dark),
    onPrimary = red.onPrimary,
    inversePrimary = red.inversePrimary,
    )
}

/** 利用者のメッセージなど、テーマの「強い色」の面と文字。ふだんは primaryContainer、OpenClaw では赤。 */
data class AccentContainer(val container: Color, val content: Color)

val LocalAccentContainer = staticCompositionLocalOf { AccentContainer(Color.Unspecified, Color.Unspecified) }

/** テーマの強い色の面。 */
@Composable
@ReadOnlyComposable
fun accentContainer(): AccentContainer = LocalAccentContainer.current.takeIf { it.container != Color.Unspecified }
    ?: AccentContainer(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)

/**
 * モダンを薄くする。背景は黒い primaryContainer へ寄せず、明るい無彩色の面へ持ち上げる（OpenClaw と同じ持ち上げ方）。
 * 強調の色（送信ボタン・リンク・透過面の色味の元）は真っ黒ではなく、背景に対して AA を保てる範囲で明るい灰色にする。
 */
internal fun softModernScheme(base: ColorScheme, dark: Boolean): ColorScheme {
    val background = if (dark) base.surface else lerp(base.surface, base.surfaceContainerLowest, OPENCLAW_BACKGROUND_LIFT)
    val bg = background.toArgb()
    var lightness = if (dark) MODERN_PRIMARY_START_DARK else MODERN_PRIMARY_START_LIGHT
    var primary = ColorUtils.HSLToColor(floatArrayOf(0f, 0f, lightness))
    repeat(20) {
        if (ColorUtils.calculateContrast(primary, bg) >= LINK_MIN_CONTRAST) return@repeat
        lightness = (if (dark) lightness + 0.02f else lightness - 0.02f).coerceIn(0f, 1f)
        primary = ColorUtils.HSLToColor(floatArrayOf(0f, 0f, lightness))
    }
    return base.copy(surface = background, background = background, primary = Color(primary))
}

/** モダンの強調の色を探し始める明るさ（ここから AA を満たすまで背景と離していく）。 */
private const val MODERN_PRIMARY_START_LIGHT = 0.46f
private const val MODERN_PRIMARY_START_DARK = 0.66f
/** モダンの強い色の面で、強調の灰色を背景に溶かす割合（大きいほど淡い）。 */
private const val MODERN_ACCENT_MIX_LIGHT = 0.8f
private const val MODERN_ACCENT_MIX_DARK = 0.7f

/** モダンはモノクロだが、エラーだけは標準の赤を残す（FR-085）。 */
internal fun withStandardError(mono: ColorScheme, dark: Boolean): ColorScheme {
    val standard = if (dark) darkColorScheme() else lightColorScheme()
    return mono.copy(
        error = standard.error,
        onError = standard.onError,
        errorContainer = standard.errorContainer,
        onErrorContainer = standard.onErrorContainer,
    )
}

/**
 * Fidelity は淡い種色だと補助の色（secondary 系）まで淡くなり、チップが背景と見分けにくくなる。
 * 全体のコントラストを上げると種色の明るさが消えるので、補助の面と文字だけをコントラストを上げた配色から取る。
 */
internal fun withContrastedSecondary(base: ColorScheme, contrasted: ColorScheme): ColorScheme = base.copy(
    secondaryContainer = contrasted.secondaryContainer,
    onSecondaryContainer = contrasted.onSecondaryContainer,
)

/**
 * リンク・強調の色を背景に対して読める明るさにする。[keepChroma] が false なら彩度も引き上げて鮮やかにする
 * （Dynamic Color など）。true なら種色の彩度のまま（カスタムのニュアンスカラーを鮮やかにしない）。
 */
internal fun vividLinkColor(base: Color, background: Color, isDark: Boolean, keepChroma: Boolean = false): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), hsl)
    if (!keepChroma && hsl[1] > 0.08f) hsl[1] = maxOf(hsl[1], 0.75f).coerceAtMost(0.92f)
    hsl[2] = if (isDark) maxOf(hsl[2], 0.68f) else minOf(hsl[2], 0.44f)
    val bgArgb = background.toArgb()
    var argb = ColorUtils.HSLToColor(hsl)
    repeat(16) {
        if (ColorUtils.calculateContrast(argb, bgArgb) >= LINK_MIN_CONTRAST) return Color(argb)
        hsl[2] = (if (isDark) hsl[2] + 0.03f else hsl[2] - 0.03f).coerceIn(0.15f, 0.92f)
        argb = ColorUtils.HSLToColor(hsl)
    }
    return Color(argb)
}

/** 本文の下地の色。背景（surface）に本文色（onSurface）を薄く重ねる（AGENTS.md 2.2）。 */
@Composable
@ReadOnlyComposable
fun bodyContainerColor(): Color {
    val c = MaterialTheme.colorScheme
    val alpha = if (c.surface.luminance() < 0.5f) BODY_OVERLAY_DARK else BODY_OVERLAY_LIGHT
    return c.onSurface.copy(alpha = alpha).compositeOver(c.surface)
}

/** シートの中で読む・書く部分の面（AGENTS.md 2.5）。 */
@Composable
@ReadOnlyComposable
fun sheetFieldColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.surface.luminance() < 0.5f) c.surfaceContainerHighest else c.surfaceContainerLowest
}

/** 本文のスタイル。bodyLarge を元に、行の高さは文字サイズ × 1.5（AGENTS.md 2.3）。 */
@Composable
@ReadOnlyComposable
fun bodyTextStyle(): TextStyle {
    val body = MaterialTheme.typography.bodyLarge
    return body.copy(lineHeight = body.fontSize * 1.5f)
}

/** コードのスタイル（等幅）。 */
@Composable
@ReadOnlyComposable
fun codeTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

/** ターミナルの配色（ARGB）。[ansi] は 0〜15 番（8〜15 は明るい側）。 */
@androidx.compose.runtime.Immutable
data class TerminalPalette(val background: Int, val foreground: Int, val cursor: Int, val ansi: List<Int>)

/**
 * ターミナルの配色（マスター指定 2026-11-23）。背景はテーマに関係なく黒に近いダークグレー（#212121）。
 * ANSI の色は彩度を落としたニュアンスカラーにして、テーマカラーを少し混ぜてアプリの色味に寄せる。
 * どの文字色も背景に対して WCAG AA（4.5:1）以上（0 番の黒は背景色として使われる前提で除く）。
 */
fun terminalPalette(primary: Color): TerminalPalette {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(primary.toArgb(), it) }
    val accentHue = hsl[0]
    fun nuance(hue: Float, saturation: Float, lightness: Float): Int {
        val muted = Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))
        val accent = Color(ColorUtils.HSLToColor(floatArrayOf(accentHue, saturation, lightness)))
        return lerp(muted, accent, TERMINAL_ACCENT_MIX).toArgb()
    }
    fun gray(lightness: Float): Int = nuance(0f, 0f, lightness)
    // 赤・緑・黄・青・紫・水色の色相（黄は黄土、紫は藤色寄り）
    val hues = listOf(8f, 140f, 42f, 215f, 300f, 182f)
    val normal = hues.map { nuance(it, TERMINAL_SATURATION, TERMINAL_LIGHTNESS) }
    val bright = hues.map { nuance(it, TERMINAL_BRIGHT_SATURATION, TERMINAL_BRIGHT_LIGHTNESS) }
    return TerminalPalette(
        background = TerminalBackground.toArgb(),
        foreground = gray(0.84f),
        // カーソルはテーマカラーを暗い背景で見える明るさにしたもの
        cursor = Color(ColorUtils.HSLToColor(floatArrayOf(accentHue, hsl[1].coerceAtMost(0.6f), 0.72f))).toArgb(),
        ansi = listOf(gray(0.30f)) + normal + listOf(gray(0.77f), gray(0.54f)) + bright + listOf(gray(0.93f)),
    )
}

/** ターミナルの背景（マスター指定 2026-11-23: #212121 ぐらいの、黒に近いダークグレー）。 */
private val TerminalBackground = Color(0xFF212121)
private const val TERMINAL_SATURATION = 0.30f
private const val TERMINAL_LIGHTNESS = 0.66f
private const val TERMINAL_BRIGHT_SATURATION = 0.38f
private const val TERMINAL_BRIGHT_LIGHTNESS = 0.76f
/** ANSI の色にテーマカラー（同じ彩度・明るさ）を混ぜる割合。 */
private const val TERMINAL_ACCENT_MIX = 0.15f

/**
 * ターミナルの上に浮く部品（特殊キーの行）の配色。ターミナルの背景はテーマに関係なく暗いので、
 * アプリの配色をもとに面・文字・強調だけをターミナルの配色に置き換える（ガラスの色味もここから作られる）。
 */
fun terminalChromeScheme(base: ColorScheme, palette: TerminalPalette): ColorScheme {
    val background = Color(palette.background)
    val foreground = Color(palette.foreground)
    val accent = Color(palette.cursor)
    return base.copy(
        surface = background,
        onSurface = foreground,
        onSurfaceVariant = Color(palette.ansi[7]),
        surfaceContainerHighest = lerp(background, foreground, TERMINAL_CHROME_RAISED),
        primary = accent,
        onPrimary = background,
        secondaryContainer = lerp(background, accent, TERMINAL_CHROME_SELECTED),
        onSecondaryContainer = foreground,
    )
}

/** 押していないキーの面（背景に文字色を混ぜる割合）。 */
private const val TERMINAL_CHROME_RAISED = 0.16f
/** 押したままのキー（Ctrl）の面（背景にテーマカラーを混ぜる割合）。 */
private const val TERMINAL_CHROME_SELECTED = 0.45f
