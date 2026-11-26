package jp.miyavi.android.clawdroid.feature.agent

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.AgentFileEntry
import jp.miyavi.android.clawdroid.core.data.AgentFileRole
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.FloatingIconButton
import jp.miyavi.android.clawdroid.core.designsystem.GlassPill
import jp.miyavi.android.clawdroid.core.designsystem.GlassSnackbarHost
import jp.miyavi.android.clawdroid.core.designsystem.PillTabs
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.feature.chat.AgentAvatar
import jp.miyavi.android.clawdroid.feature.files.LoadingOrFailed
import kotlinx.coroutines.launch

/** 設定のタブ（FR-004）。 */
/** 人格 = エージェントの人格と振る舞いを定義するファイル（AGENTS.md・SOUL.md など）とワークスペース。 */
enum class AgentSettingsTab { OVERVIEW, PERSONA, SKILLS }

/** エージェントの設定の画面（spec 004、contracts/ui.md）。ViewModel をつなぐ部分。 */
@Composable
fun AgentSettingsScreen(
    viewModel: AgentSettingsViewModel,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenWorkspace: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.skill_undo)
    val toggledOn = stringResource(R.string.skill_toggled_on)
    val toggledOff = stringResource(R.string.skill_toggled_off)
    val failed = stringResource(R.string.skill_toggle_failed)
    val failedPlain = stringResource(R.string.skill_toggle_failed_plain)
    val identitySaved = stringResource(R.string.agent_identity_saved)
    val retryLabel = stringResource(R.string.action_retry)
    val identityFailed = stringResource(R.string.agent_identity_failed)
    val identityFailedPlain = stringResource(R.string.agent_identity_failed_plain)
    // ファイルを直して戻ってきたときに、一覧（大きさ・更新日時・数）を読み直す
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AgentSettingsEvent.Toggled -> scope.launch {
                    val text = String.format(if (event.enabled) toggledOn else toggledOff, event.skill.name)
                    val result = snackbar.showSnackbar(text, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.skill, event.enabled)
                }
                is AgentSettingsEvent.ToggleFailed -> scope.launch {
                    snackbar.showSnackbar(event.message?.let { String.format(failed, it) } ?: failedPlain)
                }
                AgentSettingsEvent.IdentitySaved -> scope.launch { snackbar.showSnackbar(identitySaved) }
                AgentSettingsEvent.ConfigSaved -> Unit
                // 失敗は元の値に戻したうえで知らせ、同じ値で再試行できるようにする（マスター指定 2026-11-26）
                is AgentSettingsEvent.ConfigFailed -> scope.launch {
                    val text = event.message?.let { String.format(identityFailed, it) } ?: identityFailedPlain
                    if (snackbar.showSnackbar(text, actionLabel = retryLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        viewModel.save(event.retry)
                    }
                }
                is AgentSettingsEvent.IdentityFailed -> scope.launch {
                    snackbar.showSnackbar(event.message?.let { String.format(identityFailed, it) } ?: identityFailedPlain)
                }
            }
        }
    }
    AgentSettingsContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onOpenFile = onOpenFile,
        onOpenWorkspace = onOpenWorkspace,
        onRetryFiles = viewModel::retryFiles,
        onRetrySkills = viewModel::retrySkills,
        onRetryConfig = viewModel::retryConfig,
        onToggleSkill = { skill, enabled -> viewModel.setEnabled(skill, enabled) },
        onOpenSkill = viewModel::openSkill,
        onCloseSkill = viewModel::closeSkill,
        canEditIdentity = viewModel.canEditIdentity,
        onSaveIdentity = viewModel::saveIdentity,
        canEditModel = viewModel.canEditModel,
        canEditThinking = viewModel.canEditThinking,
        onLoadModels = viewModel::loadModelCatalog,
        onSaveModel = viewModel::saveModel,
        onSaveThinking = viewModel::saveThinking,
    )
}

@Composable
internal fun AgentSettingsContent(
    state: AgentSettingsUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenWorkspace: () -> Unit,
    onRetryFiles: () -> Unit,
    onRetrySkills: () -> Unit,
    onRetryConfig: () -> Unit,
    onToggleSkill: (jp.miyavi.android.clawdroid.core.data.SkillStatus, Boolean) -> Unit,
    onOpenSkill: (jp.miyavi.android.clawdroid.core.data.SkillStatus) -> Unit,
    onCloseSkill: () -> Unit,
    canEditIdentity: Boolean = false,
    onSaveIdentity: (String, String) -> Unit = { _, _ -> },
    canEditModel: Boolean = false,
    canEditThinking: Boolean = false,
    onLoadModels: () -> Unit = {},
    onSaveModel: (String?) -> Unit = {},
    onSaveThinking: (String?) -> Unit = {},
) {
    val hazeState = rememberHazeState()
    var tab by rememberSaveable { mutableStateOf(AgentSettingsTab.OVERVIEW) }
    var editingIdentity by rememberSaveable { mutableStateOf(false) }

    var skillQuery by rememberSaveable { mutableStateOf<String?>(null) }
    ProvideScreenHaze(hazeState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    // 取り違えないよう、タイトルには常にエージェントの名前（spec の端のケース）
                    title = {
                        Text(state.agent?.name ?: state.agentId, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        FloatingIconButton(hazeState, painterResource(R.drawable.ic_huge_chevron_left), stringResource(R.string.back_cd), onBack,
                            modifier = Modifier.padding(start = Spacing.s))
                    },
                    actions = {},
                )
            },
            snackbarHost = { GlassSnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = Dimens.minTouchTarget + Spacing.l)) },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                val contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + Dimens.minTouchTarget + Spacing.xxl,
                )
                Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                    // 選んだタブだけを描き、新しい側をフェードで出す（重い一覧どうしを同時に組み立てない。AGENTS.md 2.8）
                    FadeInTab(tab) {
                        when (tab) {
                            AgentSettingsTab.OVERVIEW -> AgentOverview(
                                state = state,
                                contentPadding = contentPadding,
                                onEditIdentity = if (canEditIdentity) ({ editingIdentity = true }) else null,
                                modelMenu = if (canEditModel) AgentMenu(modelOptions(state), currentModel(state), onLoadModels, onSaveModel) else null,
                                thinkingMenu = if (canEditThinking) AgentMenu(thinkingOptions(state), currentThinking(state), onLoadModels, onSaveThinking) else null,
                                onRetryConfig = onRetryConfig,
                            )
                            AgentSettingsTab.PERSONA -> AgentFileList(
                                files = state.files,
                                workspaceName = (state.config as? Load.Loaded)?.value?.workspaceName,
                                contentPadding = contentPadding,
                                onOpen = onOpenFile,
                                onOpenWorkspace = onOpenWorkspace,
                                onRetry = onRetryFiles,
                            )
                            AgentSettingsTab.SKILLS -> AgentSkills(
                                state = state,
                                query = skillQuery.orEmpty(),
                                contentPadding = contentPadding,
                                onRetry = onRetrySkills,
                                onToggle = onToggleSkill,
                                onOpen = onOpenSkill,
                            )
                        }
                    }
                }
                BottomBar(
                    hazeState = hazeState,
                    tab = tab,
                    onSelect = { tab = it; if (it != AgentSettingsTab.SKILLS) skillQuery = null },
                    // スキルが多いときだけ、右に検索（FR-035）
                    searchable = tab == AgentSettingsTab.SKILLS && state.skillCounts?.second?.let { it > SKILL_SEARCH_THRESHOLD } == true,
                    query = skillQuery,
                    onQuery = { skillQuery = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
    state.skillDetail?.let { detail -> SkillDetailSheet(detail, hazeState, onCloseSkill) }
    val agent = state.agent
    if (editingIdentity && agent != null) {
        IdentitySheet(
            initialName = agent.name,
            initialEmoji = agent.emoji.orEmpty(),
            saving = state.identitySaving,
            hazeState = hazeState,
            onDismiss = { editingIdentity = false },
            onSave = { name, emoji -> onSaveIdentity(name, emoji); editingIdentity = false },
        )
    }
}

/** 下部に浮くガラスのピル: タブ、スキルのタブでは検索（押すとピルが検索欄に変わる）。 */
@Composable
private fun BottomBar(
    hazeState: HazeState,
    tab: AgentSettingsTab,
    onSelect: (AgentSettingsTab) -> Unit,
    searchable: Boolean,
    query: String?,
    onQuery: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(Spacing.l)
            .widthIn(max = Dimens.maxReadingWidth),
    ) {
        if (query != null) {
            SkillSearchPill(hazeState, query, onQuery = { onQuery(it) }, onClose = { onQuery(null) })
        } else {
            GlassPill(hazeState = hazeState) {
                val labels = mapOf(
                    AgentSettingsTab.OVERVIEW to stringResource(R.string.agent_settings_tab_overview),
                    AgentSettingsTab.PERSONA to stringResource(R.string.agent_settings_tab_persona),
                    AgentSettingsTab.SKILLS to stringResource(R.string.agent_settings_tab_skills),
                )
                PillTabs(
                    tabs = AgentSettingsTab.entries,
                    selected = tab,
                    onSelect = onSelect,
                    label = { labels.getValue(it) },
                    tabHorizontalPadding = Spacing.l,
                ) { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Icon(
                            painterResource(
                                when (t) {
                                    AgentSettingsTab.OVERVIEW -> R.drawable.ic_huge_info
                                    AgentSettingsTab.PERSONA -> R.drawable.ic_huge_persona
                                    AgentSettingsTab.SKILLS -> R.drawable.ic_huge_skill
                                },
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.smallIcon),
                        )
                        Text(labels.getValue(t), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                if (searchable) {
                    androidx.compose.material3.VerticalDivider(Modifier.height(Spacing.xl).padding(horizontal = Spacing.xs))
                    androidx.compose.material3.IconButton(onClick = { onQuery("") }) {
                        Icon(painterResource(R.drawable.ic_huge_search), contentDescription = stringResource(R.string.skill_search_cd))
                    }
                }
            }
        }
    }
}

/** [key] が変わったら、新しい中身だけを不透明度 0 から出す（最初の表示では動かさない）。 */
@Composable
private fun FadeInTab(key: Any, content: @Composable () -> Unit) {
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var first by remember { mutableStateOf(true) }
    androidx.compose.runtime.key(key) {
        val alpha = remember { Animatable(if (first) 1f else 0f) }
        LaunchedEffect(Unit) {
            first = false
            alpha.animateTo(1f, spec)
        }
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) { content() }
    }
}

// ---------------------------------------------------------------- ファイル

/** 人格のタブ: 人格と振る舞いを定義するファイルの一覧と、その下にワークスペース（マスター指定 2026-11-24）。 */
@Composable
private fun AgentFileList(
    files: Load<List<AgentFileEntry>>,
    workspaceName: String?,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onOpenWorkspace: () -> Unit,
    onRetry: () -> Unit,
) {
    when (files) {
        Load.Loading -> LoadingOrFailed(failed = false, message = R.string.agent_settings_load_failed, onRetry = onRetry, modifier = Modifier.padding(contentPadding))
        is Load.Failed -> UnavailableOrFailed(files.unsupported, onRetry, Modifier.padding(contentPadding))
        is Load.Loaded -> {
            val context = LocalContext.current
            LazyColumn(contentPadding = contentPadding, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                items(files.value, key = { it.name }) { file ->
                    val role = file.role?.let { stringResource(it.label()) }
                    val status = if (file.missing) stringResource(R.string.agent_file_not_created)
                    else listOfNotNull(
                        file.size?.let { android.text.format.Formatter.formatShortFileSize(context, it) },
                        file.updatedAt?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() },
                    ).joinToString(" · ")
                    val colors = MaterialTheme.colorScheme
                    ListItem(
                        headlineContent = { Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOfNotNull(role, status.ifEmpty { null }).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                // 無いのが普通のファイルの「未作成」は目立たせない
                                color = if (file.missing && !file.expectedAbsent) colors.tertiary else colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = {
                            Icon(
                                painterResource(R.drawable.ic_huge_file),
                                contentDescription = null,
                                tint = if (file.missing) colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA) else colors.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().clickable { onOpen(file.name) },
                    )
                }
                item(key = "section:workspace") {
                    Text(
                        stringResource(R.string.agent_overview_section_workspace),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()
                            .padding(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = Spacing.xs).semantics { heading() },
                    )
                }
                item(key = "workspace") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.agent_settings_open_workspace), style = MaterialTheme.typography.titleMedium) },
                        supportingContent = {
                            Text(
                                listOfNotNull(workspaceName, stringResource(R.string.agent_settings_open_workspace_desc)).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = { Icon(painterResource(R.drawable.ic_huge_folder), contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth().clickable(onClick = onOpenWorkspace),
                    )
                }
            }
        }
    }
}

internal fun AgentFileRole.label(): Int = when (this) {
    AgentFileRole.INSTRUCTIONS -> R.string.agent_file_role_instructions
    AgentFileRole.SOUL -> R.string.agent_file_role_soul
    AgentFileRole.IDENTITY -> R.string.agent_file_role_identity
    AgentFileRole.USER -> R.string.agent_file_role_user
    AgentFileRole.HEARTBEAT -> R.string.agent_file_role_heartbeat
    AgentFileRole.MEMORY -> R.string.agent_file_role_memory
    AgentFileRole.BOOTSTRAP -> R.string.agent_file_role_bootstrap
}

/** 手段が無い（再試行なし）か、読み込みの失敗（再試行あり）。 */
@Composable
internal fun UnavailableOrFailed(unsupported: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (unsupported) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.agent_settings_unsupported),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.l),
            )
        }
    } else {
        LoadingOrFailed(failed = true, message = R.string.agent_settings_load_failed, onRetry = onRetry, modifier = modifier)
    }
}

/** 無効・折りたたみの不透明度（AGENTS.md 2.2: 0.6 まで）。 */
internal const val DISABLED_ALPHA = 0.6f

/** スキルの数がこれを超えたら検索を出す（FR-035）。 */
private const val SKILL_SEARCH_THRESHOLD = 20

/** 個別に決めている既定のモデル（全体の既定を使っていれば null）。保存中は選んだ値。 */
private fun currentModel(state: AgentSettingsUiState): String? =
    state.pendingConfig?.takeIf { it.field == ConfigField.MODEL }?.let { return it.value }
        ?: (state.config as? Load.Loaded)?.value?.model?.takeIf { !it.inherited }?.value

/** 個別に決めている思考の既定（全体の既定を使っていれば null）。保存中は選んだ値。 */
private fun currentThinking(state: AgentSettingsUiState): String? =
    state.pendingConfig?.takeIf { it.field == ConfigField.THINKING }?.let { return it.value }
        ?: (state.config as? Load.Loaded)?.value?.thinking?.takeIf { !it.inherited }?.value

/** 既定のモデルの選択肢。先頭は「全体の既定を使う」、続いて選べるモデル。読み込み中は null。 */
@Composable
private fun modelOptions(state: AgentSettingsUiState): List<AgentChoice>? {
    val config = (state.config as? Load.Loaded)?.value
    val current = currentModel(state)
    val reset = AgentChoice(
        null,
        stringResource(R.string.agent_choice_use_default),
        config?.defaultModel?.let { stringResource(R.string.agent_choice_default_value, it) },
    )
    return when (val catalog = state.modelCatalog) {
        is Load.Loaded -> listOf(reset) + catalog.value?.models.orEmpty().filter { it.selectable || it.reference == current }
            .map { AgentChoice(it.reference, it.name, it.reference.takeIf { ref -> ref != it.name }) }
        is Load.Failed -> listOf(reset)
        else -> null
    }
}

/** 思考の既定の選択肢。段階は今のモデルが受け付けるもの（分からなければエージェントの一覧の段階）。 */
@Composable
private fun thinkingOptions(state: AgentSettingsUiState): List<AgentChoice>? {
    val config = (state.config as? Load.Loaded)?.value
    val catalog = (state.modelCatalog as? Load.Loaded)?.value
    val effectiveModel = config?.model?.value ?: state.agent?.defaultModel
    val levels = catalog?.find(effectiveModel)?.thinkingLevels?.ifEmpty { null } ?: state.agent?.thinkingLevels.orEmpty()
    val reset = AgentChoice(
        null,
        stringResource(R.string.agent_choice_use_default),
        config?.defaultThinking?.let { stringResource(R.string.agent_choice_default_value, it) },
    )
    return when {
        levels.isNotEmpty() -> listOf(reset) + levels.map { AgentChoice(it.id, it.label) }
        state.modelCatalog == Load.Loading -> null
        else -> listOf(reset) + THINKING_FALLBACK.map { AgentChoice(it, it) }
    }
}

/** 段階が取れないときの並び（設定の決まり `agents.entries.*.thinkingDefault` の値）。 */
private val THINKING_FALLBACK = listOf("off", "minimal", "low", "medium", "high")
