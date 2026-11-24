package jp.miyavi.android.clawdroid.core.designsystem

import androidx.compose.material3.HorizontalDivider

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3
import jp.miyavi.android.clawdroid.core.settings.SurfaceStyle

// ガラス風の面の共通部品（AGENTS.md 2.10）。Haze の API はこのファイルの外から呼ばない。
// 値はすべて固定（設定で変えない）。Gerbil の FloatingGlassBar.kt を元にし、強さの調整は持たない。

/** 透過面の背景の不透明度（0〜1）。ガラスとぼかしで別の値。 */
data class SurfaceOpacity(val glass: Float, val blur: Float)

/** 上部のボタン・入力ピル・タブ・Snackbar など、ふつうの透過面。 */
val SURFACE_OPACITY = SurfaceOpacity(glass = 0.5f, blur = 0.3f)

/** タイトルバー（テーマカラーで色を付ける唯一の面）。色は 20%。 */
val TITLE_BAR_OPACITY = SurfaceOpacity(glass = 0.2f, blur = 0.2f)

/** 透過面の色味: 沈めたテーマカラーを背景に重ねる割合（AGENTS.md 2.10。不透明度とは別）。 */
private const val THEME_TINT_ALPHA = 0.2f

/**
 * 透過面の色味の彩度（マスター指定 2026-11-23）。テーマカラー（primary）はリンクのため彩度を強めているので、
 * 色相だけを残して彩度をこの値に置き換え、くすんだニュアンスカラーにする。黒は混ぜない（以前は黒を 50% → 25%）。
 */
private const val GLASS_TINT_SATURATION = 0.5f

/** テーマカラーの色相のまま、彩度だけを [GLASS_TINT_SATURATION] にした色。無彩色のテーマ（モダン）は無彩色のまま。 */
private fun nuanceTint(primary: Color): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(primary.toArgb(), hsl)
    if (hsl[1] > ACHROMATIC_SATURATION) hsl[1] = GLASS_TINT_SATURATION
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** くすみ色に黒（scrim）を混ぜる割合（マスター指定 2026-11-23）。 */
private const val GLASS_TINT_DARKEN = 0.3f

/** これより彩度の低い色は無彩色と見なす。 */
private const val ACHROMATIC_SATURATION = 0.08f

/** メニュー・ポップアップ。読みやすさのため 80%（マスター指定 2026-11-23。以前は 60%）。 */
val MENU_OPACITY = SurfaceOpacity(glass = 0.8f, blur = 0.8f)

/** ボトムシート。読む量が多いので濃くする。 */
val SHEET_OPACITY = SurfaceOpacity(glass = 0.9f, blur = 0.3f)

/** タイトルバーのガラスを画面の外へ広げる幅（屈折の縁の幅の最大 36dp より広く）。 */
private val TITLE_BAR_BLEED = 48.dp

/** ぼかしの強さ 10 のときの半径。強さ 0〜10 を 0〜48dp に当てる（Gerbil と同じ）。 */
private val BLUR_RADIUS_MAX = 48.dp

/**
 * 透過面の色付け（AGENTS.md 2.10）。どの面もテーマカラーを暗く沈めた色を背景の上に 20% 重ねた色味。
 * THEME はタイトルバー（その色を 20% で重ねる）、NEUTRAL はほかの面（同じ色味を面ごとの不透明度で）、FIELD は入力欄（白）。
 */
enum class GlassTint { THEME, NEUTRAL, FIELD }

/** 入力欄（会話の入力欄・ファイル内の検索欄）の面。読みやすさのため 80%（マスター指定 2026-11-23。以前は 90%）。 */
val FIELD_OPACITY = SurfaceOpacity(glass = 0.8f, blur = 0.8f)

/** 画面の透かす元。メニューや Snackbar が下の内容を透かすために、各画面で渡す。 */
val LocalScreenHazeState = staticCompositionLocalOf<HazeState?> { null }

/** 設定の透かし方（ガラス / ぼかし）で面を描く。 */
fun Modifier.hazeSurface(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    fixedOpacity: SurfaceOpacity = SURFACE_OPACITY,
    /** false なら縁の影・光・屈折を付けない（横いっぱいの帯では、縁が線や透けに見えるため）。 */
    rim: Boolean = true,
    tint: GlassTint = GlassTint.NEUTRAL,
    /** 縁で屈折させるか（既定は [rim] と同じ）。タイトルバーは縁の光と影は付けず、屈折だけさせる。 */
    refract: Boolean = rim,
    /**
     * 縁の影と光の強さ（1 = ふつう）。下に透かす内容が無い所でもガラスに見せたい面（タブの選択面）で強める。
     * 屈折は下の内容があって初めて見えるので、内容の無い背景では縁の影と光だけがガラスの手がかりになる。
     */
    rimStrength: Float = 1f,
): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    // テーマの色付け（マスター指定 2026-11-22）: テーマカラーを暗く沈めた色を、背景の上に 20% 重ねた色。全部の透過面で同じ色味にする
    val themed = lerp(nuanceTint(colors.primary), colors.scrim, GLASS_TINT_DARKEN)
    val tintColor = when (tint) {
        // タイトルバー: 沈めた色そのものを 20%（TITLE_BAR_OPACITY）で重ねる
        GlassTint.THEME -> themed
        // ほかの面: 同じ色味を、面ごとの不透明度（読みやすさのため 50〜90%）で重ねる
        GlassTint.NEUTRAL -> themed.copy(alpha = THEME_TINT_ALPHA).compositeOver(colors.surface)
        // 入力欄だけは白（マスター指定）
        GlassTint.FIELD -> sheetFieldColor()
    }
    // 下に敷く面: 入力欄は白、それ以外は画面の背景色（背景と地続きに色を乗せる）
    val base = if (tint == GlassTint.FIELD) sheetFieldColor() else colors.surface
    when (LocalSurfaceStyle.current) {
        SurfaceStyle.BLUR -> clip(shape).blurSurface(hazeState, fixedOpacity, tintColor, base)
        SurfaceStyle.GLASS -> glassSurface(hazeState, shape, fixedOpacity, rim, refract, tintColor, base, rimStrength)
    }
}

/** ボトムシートの面。 */
fun Modifier.sheetHazeSurface(hazeState: HazeState, shape: RoundedCornerShape): Modifier =
    hazeSurface(hazeState, shape, SHEET_OPACITY)

/** ガラスの上の文字・アイコンを、面の明るさに合った色で描く。 */
@Composable
fun ProvideGlassEnvironment(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val darkGlass = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassConfiguration = remember(configuration, darkGlass) {
        Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (darkGlass) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }
    CompositionLocalProvider(
        LocalConfiguration provides glassConfiguration,
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}

/**
 * 透明ガラスに、テーマカラーの濃淡と屈折を適用する。色付けは Haze のシェーダー内の tint で行う。
 * Modifier.background などで上に色を重ねると屈折と縁の光を塗りつぶすので、重ねない。
 */
private fun Modifier.glassSurface(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    fixedOpacity: SurfaceOpacity,
    rim: Boolean,
    refract: Boolean,
    tintColor: Color,
    baseColor: Color,
    rimStrength: Float = 1f,
): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    val darkGlass = colors.surface.luminance() < 0.5f
    val glassTint = tintColor.copy(alpha = fixedOpacity.glass)
    val rimShadow = colors.scrim.copy(alpha = if (!rim) 0f else ((if (darkGlass) 0.28f else 0.16f) * rimStrength).coerceAtMost(1f))
    // 屈折の強さは設定（0〜10）。横いっぱいの帯（rim = false）は屈折させない。縁の屈折が上下の端で下の内容を引き込み、1 ドット透けて見えるため
    val level = LocalGlassLevels.current.refraction.coerceIn(0, 10) / 10f
    val refraction = if (refract) level else 0f
    // ガラスはぼかさず、屈折だけで見せる（マスター指定 2026-11-23）
    val glassStyle = remember(shape, rimShadow, rim, refraction, rimStrength) {
        GlassStyle.clear.then {
            shape(shape)
            whitePoint(0f)
            surfaceProfile(SurfaceProfile.Squircle)
            ambientResponse(0.08f)
            specularIntensity(if (rim) (0.4f * rimStrength).coerceAtMost(1f) else 0f)
            edgeShadow(rimShadow)
            chromaticAberrationStrength(0.12f * refraction)
            optics {
                copy(
                    refractionStrength = refraction,
                    refractionDisplacement = (refraction * 56f).dp,
                    refractionProfile = RefractionProfile.Edge((12f + 24f * refraction).dp),
                    refractionDetailIntensity = 0f,
                )
            }
        }
        // 背景色は不透明な surface を敷く。透明だと、文字のない所で tint が消える
    }.material3(containerColor = baseColor, tint = glassTint)
    this.hazeGlass(input = HazeInput.Sources(hazeState), style = glassStyle)
}

/** 屈折のないぼかし（Haze Blur）。 */
private fun Modifier.blurSurface(hazeState: HazeState, fixedOpacity: SurfaceOpacity, tintColor: Color, baseColor: Color): Modifier = composed {
    // 下地は無彩色の面。色は tint の不透明度の分だけ
    val surface = baseColor
    val radius = BLUR_RADIUS_MAX * (LocalGlassLevels.current.blurStrength.coerceIn(0, 10) / 10f)
    val alpha = fixedOpacity.blur
    val style = remember(surface, tintColor, radius, alpha) {
        HazeBlurStyle {
            blurRadius(radius)
            // 下地は背景色（AGENTS.md 2.10）、色付けは面ごとの色
            backgroundColor(surface)
            colorEffects(listOf(HazeColorEffect.tint(tintColor.copy(alpha = alpha))))
        }
    }
    hazeBlur(input = HazeInput.Sources(hazeState), style = style)
}

/**
 * 画面の上端に横いっぱいに敷くガラス（タイトルバー、サイドパネルの看板のショーケース）。
 * 左右と上を画面の外まで広げて描き、見える縁（下）だけで屈折させる。沈めたテーマカラーを 20% で重ねる
 * （マスター指定 2026-11-23。80% → 20% → 30% → 20%）。
 */
fun Modifier.topBandGlass(hazeState: HazeState): Modifier =
    layout { measurable, constraints ->
        val bleed = TITLE_BAR_BLEED.roundToPx()
        val glass = measurable.measure(Constraints.fixed(constraints.maxWidth + bleed * 2, constraints.maxHeight + bleed))
        layout(constraints.maxWidth, constraints.maxHeight) { glass.place(-bleed, -bleed) }
    }.hazeSurface(hazeState, TopBandShape, TITLE_BAR_OPACITY, rim = false, tint = GlassTint.THEME, refract = true)

private val TopBandShape = RoundedCornerShape(0.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTitleBar(
    hazeState: HazeState,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    Box {
        // ガラスの面は、左右と上を画面の外まで広げて描く。屈折は縁で起きるので、見える縁（下）だけが屈折する
        // （画面の端で屈折させると、端に外の何も無い所が引き込まれて透けて見えるため）
        Box(
            Modifier.matchParentSize().topBandGlass(hazeState),
        )
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}

/** 透過効果のピル型の面（ボタンやボタンの組・入力欄を載せる）。 */
@Composable
fun GlassPill(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    fixedOpacity: SurfaceOpacity = SURFACE_OPACITY,
    rim: Boolean = true,
    shape: RoundedCornerShape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(Spacing.xs),
    tint: GlassTint = GlassTint.NEUTRAL,
    content: @Composable RowScope.() -> Unit,
) {
    ProvideGlassEnvironment {
        Row(
            modifier = modifier
                .hazeSurface(hazeState, shape, fixedOpacity, rim, tint)
                .clip(shape)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 透過効果の角丸の面（縦に積む中身用）。 */
@Composable
fun GlassPanel(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    fixedOpacity: SurfaceOpacity = SURFACE_OPACITY,
    shape: RoundedCornerShape = MaterialTheme.shapes.extraLarge as? RoundedCornerShape ?: RoundedCornerShape(Spacing.xl),
    tint: GlassTint = GlassTint.NEUTRAL,
    content: @Composable ColumnScope.() -> Unit,
) {
    ProvideGlassEnvironment {
        Column(
            modifier = modifier.hazeSurface(hazeState, shape, fixedOpacity, tint = tint).clip(shape),
            content = content,
        )
    }
}

/** 本文の上に浮く丸いボタン（ChatGPT 型の上部のボタン）。 */
@Composable
fun FloatingIconButton(
    hazeState: HazeState,
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassPill(hazeState = hazeState, modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Dimens.floatingButtonSize)) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

/**
 * アプリ共通のボトムシート（AGENTS.md 2.5）。面は透かし方の設定で描き、背景の不透明度はシート専用の固定値。
 * 上側だけ角丸（`shapes.extraLarge`）。高さは上部のボタンの行より上に伸びない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    hazeState: HazeState,
    sheetState: SheetState = rememberModalBottomSheetState(),
    contentModifier: Modifier = Modifier,
    /** 指定すると、シートを [hazeState] の透かす元にも加える（シートの上に別の窓で出す面から、シートが透けて見える）。 */
    hazeSourceZIndex: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val corner = MaterialTheme.shapes.extraLarge
    val sheetShape = remember(corner) {
        RoundedCornerShape(corner.topStart, corner.topEnd, CornerSize(0.dp), CornerSize(0.dp))
    }
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val statusBar = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val maxHeight = windowHeight - statusBar - Dimens.floatingButtonSize - Spacing.l
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        ProvideGlassEnvironment {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    // 透かす元の内側にシート自身の面を置く。シートの面は自分より下（会話）だけを透かす
                    .then(if (hazeSourceZIndex != null) Modifier.hazeSource(hazeState, zIndex = hazeSourceZIndex) else Modifier)
                    .sheetHazeSurface(hazeState, sheetShape)
                    .clip(sheetShape)
                    .then(contentModifier),
            ) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                content()
            }
        }
    }
}

/**
 * ガラスのメニューの区切り線。メニューの面は明るく透けるので、`outlineVariant` では見えない。
 * 一段濃い `outline` を使う（マスター指定 2026-11-23）。
 */
@Composable
fun GlassMenuDivider() {
    HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outline)
}

/** 透過効果のメニュー（AGENTS.md 2.7）。透かす元が無い画面では M3 標準の色で描く。 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = LocalScreenHazeState.current
    val shape = MaterialTheme.shapes.large as? RoundedCornerShape ?: RoundedCornerShape(CornerSize(0.dp))
    if (hazeState == null) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier, offset = offset, shape = shape, content = content)
        return
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        modifier = modifier.hazeSurface(hazeState, shape, fixedOpacity = MENU_OPACITY),
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

/** 通知（Snackbar）。M3 標準の反転色の帯の代わりに、透過効果のピルで出す（AGENTS.md 2.7）。 */
@Composable
fun GlassSnackbarHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(host, modifier) { data ->
        val hazeState = LocalScreenHazeState.current
        val shape = CircleShape
        ProvideGlassEnvironment {
            Row(
                modifier = Modifier
                    .padding(horizontal = Spacing.l, vertical = Spacing.s)
                    .widthIn(max = Dimens.maxReadingWidth)
                    .then(
                        if (hazeState != null) Modifier.hazeSurface(hazeState, shape)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
                    )
                    .clip(shape)
                    .heightIn(min = Dimens.minTouchTarget)
                    .padding(start = Spacing.xl, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    data.visuals.message,
                    modifier = Modifier.weight(1f, fill = false).padding(vertical = Spacing.m),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val action = data.visuals.actionLabel
                if (action != null) {
                    TextButton(onClick = data::performAction) { Text(action) }
                } else {
                    Spacer(Modifier.width(Spacing.m))
                }
            }
        }
    }
}

/** 画面の中身を、透かす元として共有する入れ物。 */
@Composable
fun ProvideScreenHaze(hazeState: HazeState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScreenHazeState provides hazeState) { Box { content() } }
}
