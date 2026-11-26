package jp.miyavi.android.clawdroid.feature.chat.composer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import jp.miyavi.android.clawdroid.core.data.providerBrand
import jp.miyavi.android.clawdroid.core.data.ProviderBrand
import jp.miyavi.android.clawdroid.core.data.ContextBreakdown
import jp.miyavi.android.clawdroid.core.data.HostStats
import jp.miyavi.android.clawdroid.feature.chat.SheetUsageCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import jp.miyavi.android.clawdroid.core.designsystem.GlassPanel
import androidx.compose.animation.fadeIn
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import jp.miyavi.android.clawdroid.core.designsystem.MENU_OPACITY
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.HazeState
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.ModelCatalog
import jp.miyavi.android.clawdroid.core.data.ProviderUsage
import jp.miyavi.android.clawdroid.core.data.SessionSetting
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.expandEnter
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor
import jp.miyavi.android.clawdroid.core.designsystem.shrinkExit

/** セッションのシートで、各設定を変えられるか。 */
data class SettingAvailability(val model: Boolean, val effort: Boolean, val permissions: Boolean)

/**
 * 入力欄のモデルのピルから開くシート。「モデル」の節にこのセッションのモデル・エフォート（アイコンで横 1 行）と
 * コンテキストウィンドウ、その下にプロバイダの使用量、最後に「リソース」（Gateway のホストのメモリ・ディスク）。
 * 設定はアイコンを押すと透過効果のメニューで選ぶ。実行権限は入力欄に移した（マスター指定 2026-11-26）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerSessionSheet(
    hazeState: HazeState,
    agent: Agent?,
    session: SessionSummary?,
    model: String?,
    effort: String?,
    permission: String?,
    draftOptions: Map<SessionSetting, String>,
    availability: SettingAvailability,
    canSelectFull: Boolean,
    loadModels: suspend (String, String?) -> ModelCatalog?,
    loadUsage: suspend () -> List<ProviderUsage>,
    loadContext: suspend () -> ContextBreakdown?,
    loadHostStats: suspend () -> HostStats?,
    /** 前回の値。あれば 1 フレーム目からそれで組む。 */
    cache: SheetUsageCache,
    /** 保存中の設定（その場所で読み込み表示）。 */
    saving: SessionSetting? = null,
    /** 設定ごとの保存できた回数（増えたら一瞬チェック）。 */
    savedTick: (SessionSetting) -> Int = { 0 },
    onSelect: (SessionSetting, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 開く動きと重ねない（AGENTS.md 2.8）: 取得と解析はすぐ裏で始め、画面に入れるのはシートが開き切ってから。
    // 1 フレーム目は前回の値で組み（高さを変えない）、届いた値までの差分だけ帯を動かす（マスター指定 2026-11-25）
    // 途中の高さで止めず、中身の高さまで出し切る（上限は AppBottomSheet の高さ。超えたら中だけスクロール。マスター指定 2026-11-25）
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue != SheetValue.Hidden && sheetState.currentValue == sheetState.targetValue }.first { it }
        opened = true
    }
    suspend fun awaitOpened() = snapshotFlow { opened }.first { it }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val providers by produceState(cache.providers) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            kotlinx.coroutines.coroutineScope {
                launch { cache.providers = loadUsage() }
                cache.providerUpdates.collect { loaded ->
                    awaitOpened()
                    value = loaded
                }
            }
        }
    }
    val breakdown by produceState(cache.breakdown(session?.key)) {
        val loaded = loadContext()
        awaitOpened()
        if (loaded != null || value == null) value = loaded
    }
    // Gateway のホストのメモリとディスク。シートが前に出ているあいだだけ数秒おきに取り直す（モデルのトークンは使わない）。
    // 最初に取れなければ（古い Gateway など）出さない
    val host by produceState<HostLoad>(cache.host?.let(HostLoad::Ready) ?: HostLoad.Loading) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val stats = loadHostStats()
                awaitOpened()
                value = when {
                    stats != null -> HostLoad.Ready(stats)
                    value is HostLoad.Ready -> value
                    else -> HostLoad.Unavailable
                }
                if (value == HostLoad.Unavailable) break
                delay(HOST_REFRESH_MS)
            }
        }
    }
    // モデルの一覧は、モデルかエフォートのメニューを初めて開いたときに一度だけ取る
    var wantCatalog by remember { mutableStateOf(false) }
    val catalog by produceState<ModelCatalog?>(null, wantCatalog, agent?.id, session?.key) {
        if (wantCatalog && agent != null) value = loadModels(agent.id, session?.key) ?: ModelCatalog(canReset = false)
    }
    var openSetting by remember { mutableStateOf<SessionSetting?>(null) }

    // シートを画面の透かす元にも加える（前面に重ねる）。ポップアップはシートより上の窓に出て、シートと後ろの会話の両方を透かす
    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState, sheetState = sheetState, hazeSourceZIndex = SHEET_HAZE_Z_INDEX) {
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                // 「モデル」の節: このセッションのモデル・エフォート。アイコンを横 1 行に並べ、押すとそのボタンを中心に選択肢が出る。
                // コンテキストウィンドウもモデルで上限が決まるので、この節に入れる（マスター指定 2026-11-26）
                SectionTitle(R.string.chat_setting_model)
                SettingIconRow(
                    showPermissions = false,
                    saving = saving,
                    savedTick = savedTick,
                    agent = agent,
                    session = session,
                    model = model,
                    effort = effort,
                    permission = permission,
                    availability = availability,
                    onOpen = { setting ->
                        if (setting != SessionSetting.PERMISSIONS) wantCatalog = true
                        openSetting = if (openSetting == setting) null else setting
                    },
                    popup = { setting ->
                        SettingPopup(expanded = openSetting == setting, hazeState = hazeState, onDismiss = { openSetting = null }) {
                            if (agent != null) {
                                SettingOptionsList(
                                    setting = setting,
                                    options = settingOptions(setting, agent, session, draftOptions, canSelectFull, catalog),
                                    onSelect = { value ->
                                        openSetting = null
                                        onSelect(setting, value)
                                    },
                                )
                            }
                        }
                    },
                )
                Usage(session = session, breakdown = breakdown, providers = providers, host = host)
            }
        }
    }
}

/**
 * 設定の選択肢のポップアップ。押したボタンの中心にポップアップの中心を合わせ、シートからはみ出して上に重なってよい
 * （画面の端からは出さない）。別の窓なので、外を押すか戻るで閉じる。消える動きを終えてから窓を外す（AGENTS.md 2.8）。
 */
@Composable
private fun SettingPopup(expanded: Boolean, hazeState: HazeState, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(expanded) { visible.targetState = expanded }
    if (!expanded && visible.isIdle && !visible.currentState) return
    val density = LocalDensity.current
    val margin = with(density) { Spacing.s.roundToPx() }
    val top = WindowInsets.statusBars.getTop(density) + margin
    val bottom = WindowInsets.navigationBars.getBottom(density) + margin
    // 拡大の起点は、押したボタンの位置（ポップアップは画面の端に寄せるので、ポップアップの中心からだと
    // 押した所とは別の所から出てくるように見えた。マスター指定 2026-11-26）
    val origin = remember { mutableStateOf(androidx.compose.ui.graphics.TransformOrigin.Center) }
    val provider = remember(margin, top, bottom) { CenterOnAnchor(margin, top, bottom, origin) }
    val motion = MaterialTheme.motionScheme
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
        ) {
            val scale by transition.animateFloat(
                transitionSpec = { motion.fastSpatialSpec() },
                label = "settingPopupScale",
            ) { state -> if (state == androidx.compose.animation.EnterExitState.Visible) 1f else MENU_START_SCALE }
            GlassPanel(
                hazeState = hazeState,
                fixedOpacity = MENU_OPACITY,
                shape = MaterialTheme.shapes.large as? RoundedCornerShape ?: RoundedCornerShape(Spacing.l),
                modifier = Modifier
                    .graphicsLayer {
                        transformOrigin = origin.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .width(IntrinsicSize.Max).widthIn(max = Dimens.settingMenuMaxWidth),
            ) { content() }
        }
    }
}

/**
 * ボタンの中心にポップアップの中心を合わせ、画面（システムバーの内側）からはみ出す分だけ内側に寄せる。
 * 寄せたあとのポップアップの中での、ボタンの中心の位置を [origin] に書く（拡大の起点）。
 */
private class CenterOnAnchor(
    private val margin: Int,
    private val top: Int,
    private val bottom: Int,
    private val origin: androidx.compose.runtime.MutableState<androidx.compose.ui.graphics.TransformOrigin>,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val center = anchorBounds.center
        val x = (center.x - popupContentSize.width / 2)
            .coerceAtMost(windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val y = (center.y - popupContentSize.height / 2)
            .coerceAtMost(windowSize.height - popupContentSize.height - bottom).coerceAtLeast(top)
        if (popupContentSize.width > 0 && popupContentSize.height > 0) {
            origin.value = androidx.compose.ui.graphics.TransformOrigin(
                ((center.x - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f),
                ((center.y - y).toFloat() / popupContentSize.height).coerceIn(0f, 1f),
            )
        }
        return IntOffset(x, y)
    }
}

@Composable
private fun Usage(session: SessionSummary?, breakdown: ContextBreakdown?, providers: List<ProviderUsage>?, host: HostLoad) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    // リセットまでの残りは、シートを開いた時点から数える
    val now = remember(providers) { System.currentTimeMillis() }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        val fraction = session?.contextFraction
        val used = session?.totalTokens
        val limit = session?.contextTokens
        val contextHigh = contextLevel(fraction) == ContextLevel.HIGH
        // コンテキストウィンドウ（「モデル」の節の続き）。プロバイダの使用枠と同じ帯（マスター指定 2026-11-26）:
        // 上に名前と使った割合、帯の中に「使った量 / 上限」。その下に内訳の行を横幅いっぱいで（文字 200% でも切れないように）
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.chat_context_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (fraction != null) {
                    Text(
                        percent.format(fraction),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (contextHigh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (fraction != null && limit != null) {
                UsageMeter(
                    fraction = fraction,
                    remaining = used?.let { "${compactTokens(it)} / ${compactTokens(limit)}" },
                    warn = contextHigh,
                )
            }
        }
        if (fraction != null && limit != null) {
            // 届いてから下に開く（高さが一度に変わらないように）
            AnimatedVisibility(breakdown != null, enter = expandEnter(), exit = shrinkExit()) {
                breakdown?.let { ContextRows(it) }
            }
        } else {
            MutedText(stringResource(R.string.chat_usage_unavailable))
        }

        SectionTitle(R.string.chat_usage_provider)
        // 取得中も細い読み込みの棒ではなく、帯の形を空のまま先に出す（届いたら同じ形で中身が入る。マスター指定 2026-11-25）
        AnimatedVisibility(providers == null, enter = expandEnter(), exit = shrinkExit()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                repeat(METERS_PER_ROW) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            stringResource(R.string.host_value_pending),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        UsageMeter(fraction = 0f, remaining = stringResource(R.string.host_value_pending), warn = false)
                    }
                }
            }
        }
        if (providers != null && providers.isEmpty()) MutedText(stringResource(R.string.chat_usage_unavailable))
        AnimatedVisibility(!providers.isNullOrEmpty(), enter = expandEnter(), exit = shrinkExit()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        providers?.forEach { provider ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    val brand = providerBrand(provider.ids, provider.name)
                    brand?.let {
                        Icon(painterResource(brand.icon()), contentDescription = null, modifier = Modifier.size(Spacing.l + Spacing.xs))
                    }
                    Text(when (brand) {
                        ProviderBrand.OPENAI -> "ChatGPT"
                        ProviderBrand.CLAUDE -> "Claude"
                        else -> provider.name
                    }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    provider.plan?.let { plan ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Text(plan, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs / 2))
                        }
                    }
                }
                // 枠（5時間・週間など）は 2 つずつ横に並べる（横幅を活かす。マスター指定 2026-11-24）。
                // ラベルと割合はインジケーターの上に重ねる（Control UI と同じ見せ方）
                provider.windows.chunked(METERS_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        row.forEach { window ->
                            // 枠の名前と割合は帯の上、帯の中はリセットまでの残り時間だけ（幅いっぱいに使い、切らない。マスター指定 2026-11-24）
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                val high = contextLevel(window.usedFraction) == ContextLevel.HIGH
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(windowLabel(window.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(
                                        percent.format(window.usedFraction),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (high) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                UsageMeter(
                                    fraction = window.usedFraction,
                                    remaining = window.resetAt?.let { remainingText(it - now) },
                                )
                            }
                        }
                        // 端数の枠も同じ幅にそろえる
                        repeat(METERS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        }
        }
        // プロバイダの使用枠の下に（マスター指定 2026-11-26）
        // リソース: Gateway のホストのメモリとディスク。取れない Gateway（古いものなど）では節ごと出さない。
        // 形はプロバイダの使用枠と同じ（上に名前と使った割合、帯の中に「使った量 / 全体」。マスター指定 2026-11-25）
        if (host !is HostLoad.Unavailable) {
            val ready = (host as? HostLoad.Ready)?.stats
            SectionTitle(R.string.chat_resources_title)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Meter(
                    title = R.string.host_memory_title,
                    fraction = ready?.memoryUsedFraction,
                    amount = ready?.let { usedOfTotal(it.memoryTotalBytes - it.memoryFreeBytes, it.memoryTotalBytes) },
                    warn = ready != null && ready.memoryUsedFraction >= HOST_HIGH,
                    modifier = Modifier.weight(1f),
                )
                Meter(
                    title = R.string.host_disk_title,
                    fraction = ready?.diskUsedFraction,
                    amount = ready?.let { usedOfTotal(it.diskTotalBytes - it.diskAvailableBytes, it.diskTotalBytes) },
                    warn = ready != null && ready.diskUsedFraction >= HOST_HIGH,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = Spacing.s).semantics { heading() },
    )
}

/** 内訳の 5 つの区分。 */
private enum class ContextPart(val label: Int) {
    MESSAGES(R.string.context_part_messages),
    FILES(R.string.context_part_files),
    TOOLS(R.string.context_part_tools),
    SKILLS(R.string.context_part_skills),
    SYSTEM(R.string.context_part_system),
}

private fun ContextBreakdown.parts(): List<Pair<ContextPart, Long?>> = listOf(
    ContextPart.MESSAGES to messages,
    ContextPart.FILES to files,
    ContextPart.TOOLS to tools,
    ContextPart.SKILLS to skills,
    ContextPart.SYSTEM to system,
)

/** 内訳の行（区分名・見積もりのトークン数）。帯は 1 本にまとめたので、色の四角は付けない。 */
@Composable
private fun ContextRows(breakdown: ContextBreakdown) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        breakdown.parts().forEach { (part, tokens) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(part.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    tokens?.let { "~${compactTokens(it)}" } ?: stringResource(R.string.chat_usage_unknown),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** ホストの数字の取り込みの状態。 */
private sealed interface HostLoad {
    data object Loading : HostLoad
    data object Unavailable : HostLoad
    data class Ready(val stats: HostStats) : HostLoad
}

/** シートを開いているあいだにホストの数字を取り直す間隔（マスター指定 2026-11-25）。 */
private const val HOST_REFRESH_MS = 5_000L
/** メモリ・ディスクの使用の割合がこれ以上なら警告の色にする。 */
private const val HOST_HIGH = 0.9f

/** 3 等分の 1 つ。プロバイダの使用枠と同じ帯（上に名前、帯の中に量）。取得中は量を「—」。 */
@Composable
private fun Meter(title: Int, fraction: Float?, amount: String?, warn: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // 割合は出さない（帯の長さで分かる。マスター指定 2026-11-25）
        Text(stringResource(title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        UsageMeter(fraction = fraction ?: 0f, remaining = amount ?: stringResource(R.string.host_value_pending), warn = warn)
    }
}

/**
 * 「9.5 / 15 GiB」「512 MiB / 15 GiB」。使った量と全体を、帯の幅に収まるよう単位を 1 つにまとめて出す
 * （1024 単位なので単位も GiB・MiB。10 未満だけ小数 1 桁。マスター指定 2026-11-25）。
 */
@Composable
private fun usedOfTotal(used: Long, total: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    fun parts(bytes: Long): Pair<String, Int> {
        val gib = bytes / BYTES_PER_GIB
        val (number, unit) = if (gib >= 1) gib to R.string.host_bytes_gb else bytes / BYTES_PER_MIB to R.string.host_bytes_mb
        val digits = if (number < 10) 1 else 0
        // 10 未満は小数 1 桁を必ず出す（5 と 5.1 で桁が揺れないように。マスター指定 2026-11-25）
        val format = NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = digits; maximumFractionDigits = digits }
        return format.format(number) to unit
    }
    val (usedNumber, usedUnit) = parts(used.coerceAtLeast(0))
    val (totalNumber, totalUnit) = parts(total)
    val totalText = stringResource(totalUnit, totalNumber)
    return if (usedUnit == totalUnit) stringResource(R.string.host_amount, usedNumber, totalText)
    else stringResource(R.string.host_amount, stringResource(usedUnit, usedNumber), totalText)
}

private const val BYTES_PER_GIB = 1024.0 * 1024 * 1024
private const val BYTES_PER_MIB = 1024.0 * 1024

/** 「46.6k」「1.2M」「797」。 */
private fun compactTokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
    n >= 1_000 -> "%.1fk".format(n / 1_000.0).replace(".0k", "k")
    else -> n.toString()
}

private fun ProviderBrand.icon(): Int = when (this) {
    ProviderBrand.CLAUDE -> R.drawable.ic_huge_brand_claude
    ProviderBrand.OPENAI -> R.drawable.ic_huge_brand_openai
    ProviderBrand.GEMINI -> R.drawable.ic_huge_brand_gemini
    ProviderBrand.DEEPSEEK -> R.drawable.ic_huge_brand_deepseek
    ProviderBrand.GROK -> R.drawable.ic_huge_brand_grok
    ProviderBrand.MISTRAL -> R.drawable.ic_huge_brand_mistral
    ProviderBrand.META -> R.drawable.ic_huge_brand_meta
    ProviderBrand.PERPLEXITY -> R.drawable.ic_huge_brand_perplexity
    ProviderBrand.QWEN -> R.drawable.ic_huge_brand_qwen
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * 使用量の 1 行。ラベルと割合の下に細い棒を 1 本だけ引く（枠や塗りの面は重ねない）。
 * 上限に近いと棒を error の色にする。
 */
/**
 * 使用量の 1 行。角丸の帯の中を使った割合だけ塗り、その上にラベル・リセット時刻・割合を重ねる。
 * 塗りはテーマの強い色、上限に近い（80% 以上）ときは error のくすみ色。伸びはばねで動く。
 */
@Composable
private fun UsageMeter(
    fraction: Float,
    remaining: String? = null,
    warn: Boolean = contextLevel(fraction) == ContextLevel.HIGH,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    // 前回の値（シートを開いた時点の値）から、届いた値までの差分だけ動かす。0 からは伸ばさない（マスター指定 2026-11-25）
    val progress = remember { Animatable(fraction.coerceIn(0f, 1f)) }
    LaunchedEffect(fraction) { progress.animateTo(fraction.coerceIn(0f, 1f), motion.defaultSpatialSpec()) }
    // 塗りは文字が読める淡い色、使った所の下の縁にはっきりした強調の色の線を引く（下地との差が分かるように）
    val strong = if (warn) colors.error else colors.primary
    val fill = lerp(strong, colors.surface, METER_FILL_MIX)
    val density = LocalDensity.current
    val edge = with(density) { Dimens.usageMeterEdge.toPx() }
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.compactTouchTarget + Spacing.s)
            .clip(shape)
            .background(colors.surfaceContainer)
            .drawBehind {
                val width = size.width * progress.value
                drawRect(fill, size = size.copy(width = width))
                drawRect(strong, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - edge), size = androidx.compose.ui.geometry.Size(width, edge))
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (remaining != null) {
                // 切らずに出し切る（文字が大きいときは折り返して帯が高くなる）
                Text(remaining, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
            }
        }
    }
}

/** リセットまでの残り（あと1日16時間・あと16時間42分・あと42分）。過ぎていれば「まもなく」。 */
@Composable
private fun remainingText(millis: Long): String {
    val (days, hours, minutes) = remainingParts(millis) ?: return stringResource(R.string.usage_reset_soon)
    return when {
        days > 0 -> stringResource(R.string.usage_remaining_days, days, hours)
        hours > 0 -> stringResource(R.string.usage_remaining_hours, hours, minutes)
        else -> stringResource(R.string.usage_remaining_minutes, minutes.coerceAtLeast(1))
    }
}

/** 残りを 日・時間・分 に分ける（分は切り上げ）。過ぎていれば null。 */
internal fun remainingParts(millis: Long): Triple<Long, Long, Long>? {
    if (millis <= 0) return null
    val totalMinutes = (millis + MINUTE_MS - 1) / MINUTE_MS
    return Triple(totalMinutes / (24 * 60), totalMinutes / 60 % 24, totalMinutes % 60)
}

private const val MINUTE_MS = 60_000L

/** 使用量の枠を 1 行に並べる数。 */
private const val METERS_PER_ROW = 2

/** 使用量の塗りで、強調の色を背景に溶かす割合（文字が読める淡さ）。 */
private const val METER_FILL_MIX = 0.78f

/** Gateway の窓の名前（5h・Week など）を、分かるものだけ訳す。 */
@Composable
private fun windowLabel(label: String): String = when (label.trim().lowercase()) {
    "5h", "5 hours", "5-hour" -> stringResource(R.string.usage_window_5h)
    "week", "weekly", "7d" -> stringResource(R.string.usage_window_week)
    "day", "daily", "24h" -> stringResource(R.string.usage_window_day)
    else -> label
}



/** ポップアップが出るときの最初の大きさ（M3 のメニューと同じ 0.8 倍）。 */
private const val MENU_START_SCALE = 0.8f

/** シートを画面の透かす元に加えるときの重なり順（会話の上）。 */
private const val SHEET_HAZE_Z_INDEX = 1f
