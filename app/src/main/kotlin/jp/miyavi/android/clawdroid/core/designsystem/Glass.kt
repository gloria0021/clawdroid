package jp.miyavi.android.clawdroid.core.designsystem

import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
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

/** タイトルバーの色を暗く沈める割合。 */
private const val TITLE_BAR_DARKEN = 0.5f

/** メニュー。文字を選ぶ面なので濃くする。 */
val MENU_OPACITY = SurfaceOpacity(glass = 0.9f, blur = 0.3f)

/** ボトムシート。読む量が多いので濃くする。 */
val SHEET_OPACITY = SurfaceOpacity(glass = 0.9f, blur = 0.3f)

/** ぼかしの強さ 10 のときの半径。強さ 0〜10 を 0〜48dp に当てる（Gerbil と同じ）。 */
private val BLUR_RADIUS_MAX = 48.dp

/**
 * 透過面の色付け。テーマカラーで色を付けてよいのは AppTitleBar だけ（AGENTS.md 2.10、マスター指定）。
 * それ以外はすべて無彩色の面の色（ライト = surfaceContainerLowest、ダーク = surfaceContainerHighest）。
 * `surface` は背景をテーマカラーへ寄せてあるので、ガラスの色には使わない。
 */
enum class GlassTint { THEME, NEUTRAL, FIELD }

/** 入力欄の面の不透明度（白く見せるため濃くする）。 */
val FIELD_OPACITY = SurfaceOpacity(glass = 0.9f, blur = 0.9f)

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
): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    val tintColor = when (tint) {
        // タイトルバーはテーマカラーを暗く沈めた色（primary と scrim の中間）を薄く重ねる。背景より少し暗く、ほんのり色が入る
        GlassTint.THEME -> lerp(colors.primary, colors.scrim, TITLE_BAR_DARKEN)
        GlassTint.NEUTRAL, GlassTint.FIELD -> sheetFieldColor()
    }
    // 下に敷く面は常に無彩色。テーマカラーは不透明度の分だけ重ねる（敷く面に色を使うと、不透明度に関係なく色が付くため）
    // タイトルバーの下地は画面の背景色（白ではなく、背景と地続きにほんのり色を乗せる）
    val base = if (tint == GlassTint.THEME) colors.surface else sheetFieldColor()
    when (LocalSurfaceStyle.current) {
        SurfaceStyle.BLUR -> clip(shape).blurSurface(hazeState, fixedOpacity, tintColor, base)
        SurfaceStyle.GLASS -> glassSurface(hazeState, shape, fixedOpacity, rim, tintColor, base)
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
    tintColor: Color,
    baseColor: Color,
): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    val darkGlass = colors.surface.luminance() < 0.5f
    val glassTint = tintColor.copy(alpha = fixedOpacity.glass)
    val rimShadow = colors.scrim.copy(alpha = if (!rim) 0f else if (darkGlass) 0.28f else 0.16f)
    // 屈折の強さは設定（0〜10）。横いっぱいの帯（rim = false）は屈折させない。縁の屈折が上下の端で下の内容を引き込み、1 ドット透けて見えるため
    val level = LocalGlassLevels.current.refraction.coerceIn(0, 10) / 10f
    val refraction = if (rim) level else 0f
    val glassStyle = remember(shape, rimShadow, rim, refraction) {
        GlassStyle.clear.then {
            shape(shape)
            whitePoint(0f)
            surfaceProfile(SurfaceProfile.Squircle)
            ambientResponse(0.08f)
            specularIntensity(if (rim) 0.4f else 0f)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTitleBar(
    hazeState: HazeState,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val shape = remember { RoundedCornerShape(0.dp) }
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        modifier = Modifier.hazeSurface(hazeState, shape, TITLE_BAR_OPACITY, rim = false, tint = GlassTint.THEME),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
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
    content: @Composable RowScope.() -> Unit,
) {
    ProvideGlassEnvironment {
        Row(
            modifier = modifier
                .hazeSurface(hazeState, shape, fixedOpacity, rim)
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
