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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 入力欄のモデルのピルから開くシート。上にこのセッションの設定（モデル・エフォート・実行権限）をアイコンで横 1 行、
 * その下にコンテキストとプロバイダの使用量。設定はアイコンを押すと透過効果のメニューで選ぶ。
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
    onSelect: (SessionSetting, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val providers by produceState<List<ProviderUsage>?>(null) { value = loadUsage() }
    val breakdown by produceState<ContextBreakdown?>(null) { value = loadContext() }
    // モデルの一覧は、モデルかエフォートのメニューを初めて開いたときに一度だけ取る
    var wantCatalog by remember { mutableStateOf(false) }
    val catalog by produceState<ModelCatalog?>(null, wantCatalog, agent?.id, session?.key) {
        if (wantCatalog && agent != null) value = loadModels(agent.id, session?.key) ?: ModelCatalog(canReset = false)
    }
    var openSetting by remember { mutableStateOf<SessionSetting?>(null) }

    // シートを画面の透かす元にも加える（前面に重ねる）。ポップアップはシートより上の窓に出て、シートと後ろの会話の両方を透かす
    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState, hazeSourceZIndex = SHEET_HAZE_Z_INDEX) {
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                // このセッションの設定（モデル・エフォート・実行権限）。アイコンを横 1 行に並べ、押すとそのボタンを中心に選択肢が出る
                SettingIconRow(
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
                Usage(session = session, breakdown = breakdown, providers = providers)
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
    val provider = remember(margin, top, bottom) { CenterOnAnchor(margin, top, bottom) }
    val motion = MaterialTheme.motionScheme
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = visible,
            enter = scaleIn(motion.fastSpatialSpec(), initialScale = MENU_START_SCALE) + fadeIn(motion.defaultEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec(), targetScale = MENU_START_SCALE) + fadeOut(motion.fastEffectsSpec()),
        ) {
            GlassPanel(
                hazeState = hazeState,
                fixedOpacity = MENU_OPACITY,
                shape = MaterialTheme.shapes.large as? RoundedCornerShape ?: RoundedCornerShape(Spacing.l),
                modifier = Modifier.width(IntrinsicSize.Max).widthIn(max = Dimens.settingMenuMaxWidth),
            ) { content() }
        }
    }
}

/** ボタンの中心にポップアップの中心を合わせ、画面（システムバーの内側）からはみ出す分だけ内側に寄せる。 */
private class CenterOnAnchor(private val margin: Int, private val top: Int, private val bottom: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val center = anchorBounds.center
        val x = (center.x - popupContentSize.width / 2)
            .coerceAtMost(windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val y = (center.y - popupContentSize.height / 2)
            .coerceAtMost(windowSize.height - popupContentSize.height - bottom).coerceAtLeast(top)
        return IntOffset(x, y)
    }
}

@Composable
private fun Usage(session: SessionSummary?, breakdown: ContextBreakdown?, providers: List<ProviderUsage>?) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val timeOnly = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    val dateAndTime = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        // コンテキストウィンドウ: 見出しの右に「使った量 / 上限 · 割合」、下に内訳の色で塗り分けた細い棒と内訳の行
        val fraction = session?.contextFraction
        val used = session?.totalTokens
        val limit = session?.contextTokens
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(R.string.chat_context_title, Modifier.weight(1f))
            if (fraction != null && used != null && limit != null) {
                Text(
                    "${compactTokens(used)} / ${compactTokens(limit)} · ${percent.format(fraction)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (contextLevel(fraction) == ContextLevel.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (fraction != null && limit != null) {
            ContextBar(breakdown, limit, fraction)
            breakdown?.let { ContextRows(it) }
        } else {
            MutedText(stringResource(R.string.chat_usage_unavailable))
        }
        val input = session?.inputTokens
        val output = session?.outputTokens
        if (input != null || output != null) {
            val unknown = stringResource(R.string.chat_usage_unknown)
            MutedText(stringResource(R.string.chat_usage_tokens, input?.let(numbers::format) ?: unknown, output?.let(numbers::format) ?: unknown))
        }

        SectionTitle(R.string.chat_usage_provider)
        AnimatedVisibility(providers == null, enter = expandEnter(), exit = shrinkExit()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (providers != null && providers.isEmpty()) MutedText(stringResource(R.string.chat_usage_unavailable))
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
                // 枠（5時間・週間など）は 1 枠 1 行。ラベルと割合をインジケーターの上に重ねる（Control UI と同じ見せ方）
                provider.windows.forEach { window ->
                    UsageMeter(
                        fraction = window.usedFraction,
                        label = windowLabel(window.label),
                        percentage = percent.format(window.usedFraction),
                        reset = window.resetAt?.let { (if (android.text.format.DateUtils.isToday(it)) timeOnly else dateAndTime).format(Date(it)) },
                    )
                }
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

/** 内訳の 5 つの区分と色（区分を見分けるための色。AGENTS.md 2.2 の「意味のある色分け」）。 */
private enum class ContextPart(val label: Int) {
    MESSAGES(R.string.context_part_messages),
    FILES(R.string.context_part_files),
    TOOLS(R.string.context_part_tools),
    SKILLS(R.string.context_part_skills),
    SYSTEM(R.string.context_part_system),
}

@Composable
private fun ContextPart.color(): Color = when (this) {
    ContextPart.MESSAGES -> MaterialTheme.colorScheme.primary
    ContextPart.FILES -> MaterialTheme.colorScheme.tertiary
    ContextPart.TOOLS -> MaterialTheme.colorScheme.secondary
    ContextPart.SKILLS -> MaterialTheme.colorScheme.error
    ContextPart.SYSTEM -> MaterialTheme.colorScheme.outline
}

private fun ContextBreakdown.parts(): List<Pair<ContextPart, Long?>> = listOf(
    ContextPart.MESSAGES to messages,
    ContextPart.FILES to files,
    ContextPart.TOOLS to tools,
    ContextPart.SKILLS to skills,
    ContextPart.SYSTEM to system,
)

/** 細い棒。内訳があれば区分ごとに塗り分け、なければ使った割合を 1 色で。伸びはばね。 */
@Composable
private fun ContextBar(breakdown: ContextBreakdown?, limit: Long, fraction: Float) {
    val motion = MaterialTheme.motionScheme
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, motion.defaultSpatialSpec()) }
    val colors = MaterialTheme.colorScheme
    val track = colors.surfaceContainerHighest
    // 内訳が無いときは 1 色の帯。くすみ色だと下地と見分けにくいので、はっきりした強調の色（残りが少ないときはエラーの色）
    val single = if (contextLevel(fraction) == ContextLevel.HIGH) colors.error else colors.primary
    val segments = breakdown?.parts()?.mapNotNull { (part, tokens) -> tokens?.let { part.color() to it.toFloat() / limit } }
        ?: listOf(single to fraction)
    Box(
        Modifier.fillMaxWidth().height(Dimens.usageBarHeight).clip(CircleShape).background(track).drawBehind {
            var x = 0f
            segments.forEach { (color, share) ->
                val w = size.width * share.coerceIn(0f, 1f) * grow.value
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = size.copy(width = w))
                x += w
            }
        },
    )
}

/** 内訳の行（色の四角・区分名・見積もりのトークン数）。 */
@Composable
private fun ContextRows(breakdown: ContextBreakdown) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        breakdown.parts().forEach { (part, tokens) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Box(Modifier.size(Spacing.m).clip(MaterialTheme.shapes.extraSmall).background(part.color()))
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
private fun UsageMeter(fraction: Float, label: String, percentage: String, reset: String? = null) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fraction) { progress.animateTo(fraction.coerceIn(0f, 1f), motion.defaultSpatialSpec()) }
    val high = contextLevel(fraction) == ContextLevel.HIGH
    // 塗りは文字が読める淡い色、使った所の下の縁にはっきりした強調の色の線を引く（下地との差が分かるように）
    val strong = if (high) colors.error else colors.primary
    val fill = lerp(strong, colors.surface, METER_FILL_MIX)
    val density = LocalDensity.current
    val edge = with(density) { Dimens.usageMeterEdge.toPx() }
    val shape = MaterialTheme.shapes.medium
    Box(
        Modifier
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
            if (reset != null) {
                Icon(painterResource(R.drawable.ic_huge_refresh), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Spacing.m + Spacing.xs))
                Text(reset, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text(percentage, style = MaterialTheme.typography.labelLarge, color = if (high) colors.error else colors.onSurface)
        }
    }
}

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
