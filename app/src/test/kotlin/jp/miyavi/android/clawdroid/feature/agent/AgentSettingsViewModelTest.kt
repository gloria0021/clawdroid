package jp.miyavi.android.clawdroid.feature.agent

import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.AgentFileEntry
import jp.miyavi.android.clawdroid.core.data.SkillMissing
import jp.miyavi.android.clawdroid.core.data.SkillSource
import jp.miyavi.android.clawdroid.core.data.SkillState
import jp.miyavi.android.clawdroid.core.data.SkillStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** エージェントの設定の状態（spec 004 US1・US3）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentSettingsViewModelTest {
    private fun skill(key: String, state: SkillState) =
        SkillStatus(key, key, null, null, "/s/$key", source = null, state = state, missing = SkillMissing(), blockedByAllowlist = false, blockedByAgentFilter = false)

    private inner class FakeSkills : SkillSource {
        var list = listOf(skill("a", SkillState.ENABLED), skill("b", SkillState.DISABLED))
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        val updates = mutableListOf<Pair<String, Boolean>>()
        override suspend fun status(agentId: String) = list
        override suspend fun readSkillFile(agentId: String, filePath: String) = "# Skill"
        override suspend fun setEnabled(skillKey: String, enabled: Boolean) {
            gate?.await()
            if (fail) error("denied")
            updates += skillKey to enabled
            list = list.map { if (it.skillKey == skillKey) it.copy(state = if (enabled) SkillState.ENABLED else SkillState.DISABLED) else it }
        }
    }

    private val skills = FakeSkills()
    private val agents = MutableStateFlow(listOf(Agent("x", "Kino", null, null, "claude/opus", isSystem = false)) to false)
    private var methods = setOf<String>()
    private var refreshed = 0
    private val identityUpdates = mutableListOf<Triple<String, String, String>>()
    private val modelUpdates = mutableListOf<String?>()
    private val thinkingUpdates = mutableListOf<Pair<String?, String?>>()
    private var configFails = false
    private val configs = object : jp.miyavi.android.clawdroid.core.data.AgentConfigSource {
        override suspend fun load(agentId: String) = jp.miyavi.android.clawdroid.core.data.AgentConfig(
            model = null, thinking = null, toolProfile = null, allow = emptyList(), deny = emptyList(), execMode = null,
            elevated = null, workspaceOnly = null, webSearch = null, webSearchProvider = null, workspaceName = null, baseHash = "h1",
        )
        override suspend fun updateIdentity(agentId: String, name: String, emoji: String) { identityUpdates += Triple(agentId, name, emoji) }
        override suspend fun setModel(agentId: String, model: String?) { if (configFails) error("denied"); modelUpdates += model }
        override suspend fun setThinking(agentId: String, level: String?, baseHash: String?) { thinkingUpdates += level to baseHash }
    }
    private fun viewModel(files: AgentFilesLoader = AgentFilesLoader { listOf(AgentFileEntry("AGENTS.md", false, false, 1, null)) }) =
        AgentSettingsViewModel("x", agents, files, skills, configs, supports = { methods.isEmpty() || it in methods }, refreshAgents = { refreshed++ })

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loads_agentFilesAndSkills() {
        val vm = viewModel()
        val s = vm.state.value
        assertEquals("Kino", s.agent?.name)
        assertEquals(1, s.fileCount)
        assertEquals(1 to 2, s.skillCounts)
    }

    @Test fun files_failure_and_unsupported() {
        val failing = viewModel(files = AgentFilesLoader { error("offline") })
        assertEquals(Load.Failed(unsupported = false), failing.state.value.files)
        methods = setOf("skills.status")
        val old = viewModel()
        assertEquals(Load.Failed(unsupported = true), old.state.value.files)
        assertTrue(old.state.value.skills is Load.Loaded)
    }

    @Test fun toggle_showsFirst_thenConfirms() {
        val vm = viewModel()
        val target = skill("a", SkillState.ENABLED)
        skills.gate = CompletableDeferred()
        vm.setEnabled(target, false)
        // 通信の結果を待たずに見た目が変わる（SC-006）
        assertFalse(vm.state.value.enabled(target))
        skills.gate!!.complete(Unit)
        assertTrue(vm.state.value.pendingToggles.isEmpty())
        val updated = (vm.state.value.skills as Load.Loaded).value.first { it.skillKey == "a" }
        assertEquals(SkillState.DISABLED, updated.state)
        assertEquals(listOf("a" to false), skills.updates)
    }

    @Test fun toggle_failure_revertsAndReports() {
        val vm = viewModel()
        val target = skill("a", SkillState.ENABLED)
        skills.fail = true
        vm.setEnabled(target, false)
        assertTrue(vm.state.value.enabled(target))
        assertTrue(vm.state.value.pendingToggles.isEmpty())
    }

    @Test fun undo_flipsBack() {
        val vm = viewModel()
        val target = skill("a", SkillState.ENABLED)
        vm.setEnabled(target, false)
        vm.undo(target, enabled = false)
        assertEquals(listOf("a" to false, "a" to true), skills.updates)
    }

    @Test fun openSkill_parsesMarkdownOnce() {
        val vm = viewModel()
        vm.openSkill(skill("a", SkillState.ENABLED))
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.state.value.skillDetail !is SkillDetail.Ready && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(vm.state.value.skillDetail is SkillDetail.Ready)
        vm.closeSkill()
        assertEquals(null, vm.state.value.skillDetail)
    }

    @Test fun saveIdentity_updatesAndRefreshesAgents() {
        val vm = viewModel()
        assertTrue(vm.canEditIdentity)
        vm.saveIdentity("  Kino 2  ", "🦀")
        assertEquals(listOf(Triple("x", "Kino 2", "🦀")), identityUpdates)
        assertEquals(1, refreshed)
        assertFalse(vm.state.value.identitySaving)
        vm.saveIdentity("   ", "")
        assertEquals(1, identityUpdates.size)
    }

    @Test fun identityEdit_hiddenWithoutAgentsUpdate() {
        methods = setOf("skills.status")
        assertFalse(viewModel().canEditIdentity)
    }

    @Test fun saveModel_andResetToDefault() {
        val vm = viewModel()
        vm.saveModel("anthropic/claude-opus-5-5")
        vm.saveModel(null)
        assertEquals(listOf("anthropic/claude-opus-5-5", null), modelUpdates)
        assertEquals(2, refreshed)
        assertFalse(vm.state.value.configSaving)
    }

    @Test fun saveThinking_sendsBaseHash() {
        val vm = viewModel()
        vm.saveThinking("high")
        assertEquals(listOf("high" to "h1"), thinkingUpdates)
    }

    @Test fun saveModel_failure_clearsSaving() {
        configFails = true
        val vm = viewModel()
        vm.saveModel("x/y")
        assertTrue(modelUpdates.isEmpty())
        assertFalse(vm.state.value.configSaving)
    }

    @Test fun saveModel_success_countsSaved_andClearsPending() {
        val vm = viewModel()
        vm.saveModel("anthropic/claude-opus-5-5")
        assertEquals(null, vm.state.value.pendingConfig)
        assertEquals(ConfigField.MODEL to 1, vm.state.value.configSaved)
        vm.saveThinking("high")
        assertEquals(ConfigField.THINKING to 2, vm.state.value.configSaved)
    }

    @Test fun saveModel_failure_dropsPending_andKeepsSavedCount() {
        configFails = true
        val vm = viewModel()
        vm.saveModel("x/y")
        assertEquals(null, vm.state.value.pendingConfig)
        assertEquals(null, vm.state.value.configSaved)
    }
}
