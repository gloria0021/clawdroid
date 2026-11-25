package jp.miyavi.android.clawdroid.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.designsystem.AppBottomSheet
import jp.miyavi.android.clawdroid.core.designsystem.AppTitleBar
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.ProvideScreenHaze
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.core.designsystem.sheetFieldColor
import jp.miyavi.android.clawdroid.core.licenses.OssEntry
import jp.miyavi.android.clawdroid.core.licenses.OssLicenses

/** 一覧の読み込みの状態。 */
private sealed interface LicensesLoad {
    data object Loading : LicensesLoad
    data object Failed : LicensesLoad
    data class Ready(val entries: List<OssEntry>) : LicensesLoad
}

/**
 * オープンソースのライセンスの一覧（spec 003 FR-033・035）。名前の順のシンプルなリストで、行は名前と許諾の種類。
 * 押すと共通のボトムシートの不透明な面で全文を読む（長い文は面の中だけスクロール）。端末内の文だけを使う。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val load by produceState<LicensesLoad>(LicensesLoad.Loading) {
        value = runCatching { OssLicenses.load(context) }.fold({ LicensesLoad.Ready(it) }, { LicensesLoad.Failed })
    }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    ProvideScreenHaze(hazeState) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppTitleBar(
                    hazeState = hazeState,
                    title = { Text(stringResource(R.string.licenses_title), style = MaterialTheme.typography.titleMedium, maxLines = 2) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_huge_arrow_left), contentDescription = stringResource(R.string.back_cd))
                        }
                    },
                    actions = {},
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            when (val current = load) {
                LicensesLoad.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
                LicensesLoad.Failed -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.licenses_load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is LicensesLoad.Ready -> {
                    LazyColumn(
                        Modifier.fillMaxSize().hazeSource(hazeState),
                        contentPadding = padding,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        items(current.entries, key = { it.id }) { entry ->
                            val label = stringResource(R.string.licenses_open_cd, entry.name)
                            ListItem(
                                headlineContent = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(entry.licenseNames.ifEmpty { null }, entry.version).joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                colors = transparentItem(),
                                modifier = Modifier.widthIn(max = Dimens.maxReadingWidth).fillMaxWidth()
                                    .clickable(role = Role.Button, onClickLabel = label) { openedId = entry.id },
                            )
                        }
                    }
                    current.entries.firstOrNull { it.id == openedId }?.let { entry ->
                        LicenseSheet(entry, hazeState) { openedId = null }
                    }
                }
            }
        }
    }
}

/** 許諾文の全文。シートより明るい不透明な面に載せ、その面の中だけをスクロールする（AGENTS.md 2.5）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseSheet(entry: OssEntry, hazeState: dev.chrisbanes.haze.HazeState, onDismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(entry.licenseNames.ifEmpty { null }, entry.version).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            color = sheetFieldColor(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s).navigationBarsPadding(),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                val texts = entry.licenses.mapNotNull { it.text ?: it.url }
                if (texts.isEmpty()) {
                    Text(stringResource(R.string.licenses_no_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                texts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
