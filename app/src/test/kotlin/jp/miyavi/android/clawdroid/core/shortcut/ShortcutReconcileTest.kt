package jp.miyavi.android.clawdroid.core.shortcut

import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.data.AgentsState
import jp.miyavi.android.clawdroid.core.data.SessionListState
import jp.miyavi.android.clawdroid.core.data.SessionSummary
import jp.miyavi.android.clawdroid.core.notify.OpenTarget
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** spec 007: 片付けの判定（R4）・名前（R6）・保存の形（R7）。 */
class ShortcutReconcileTest {
    private fun agent(id: String, name: String = id) = Agent(id = id, name = name, emoji = null, avatarUrl = null, defaultModel = null, isSystem = false)

    private fun session(key: String, title: String? = key, cwd: String? = null, archived: Boolean = false) = SessionSummary(
        key = key, agentId = "kino", sessionId = null, title = title, cwd = cwd, model = null, thinkingLevel = null,
        running = false, unread = false, archived = archived, isAutomation = false, updatedAt = 1,
        contextTokens = null, totalTokens = null, preview = null,
    )

    private val agents = AgentsState(listOf(agent("kino", "キノ"), agent("moon", "Moonshot")), loading = false)
    private val home = session("agent:kino:main", isMain = true)
    private fun session(key: String, isMain: Boolean) = session(key).copy(isMain = isMain)

    private fun lists(active: List<SessionSummary> = emptyList(), archived: List<SessionSummary> = emptyList(), loading: Boolean = false, error: String? = null) =
        mapOf("kino" to SessionListState(sessions = active, archived = archived, home = home, loading = loading, error = error))

    private fun slot(id: String, key: String, archived: Boolean = false) = ShortcutSlot(id, "kino", ShortcutDestination.Session(key), archived = archived)

    @Test fun active_staysAsIs() {
        val slots = listOf(slot("s1", "k1"))
        val r = reconcile(slots, lists(active = listOf(session("k1"))), agents)
        assertEquals(slots, r.slots)
        assertTrue(r.disable.isEmpty())
        assertTrue(r.enable.isEmpty())
    }

    @Test fun archived_isMarkedAndDisabled_thenRestoredWhenUnarchived() {
        val r = reconcile(listOf(slot("s1", "k1")), lists(archived = listOf(session("k1", archived = true))), agents)
        assertEquals(listOf(slot("s1", "k1", archived = true)), r.slots)
        assertEquals(mapOf("s1" to DisableReason.ARCHIVED), r.disable)
        // もう一度判定しても、二重に無効にしない
        val again = reconcile(r.slots, lists(archived = listOf(session("k1", archived = true))), agents)
        assertTrue(again.disable.isEmpty())
        // アーカイブが戻された
        val back = reconcile(r.slots, lists(active = listOf(session("k1"))), agents)
        assertEquals(listOf(slot("s1", "k1")), back.slots)
        assertEquals(setOf("s1"), back.enable)
    }

    @Test fun deleted_removesTheSlot() {
        val r = reconcile(listOf(slot("s1", "k1"), slot("s2", "k2")), lists(active = listOf(session("k2"))), agents)
        assertEquals(listOf("s2"), r.slots.map { it.id })
        assertEquals(mapOf("s1" to DisableReason.DELETED), r.disable)
    }

    @Test fun unloadedOrFailedList_isNotJudged() {
        val slots = listOf(slot("s1", "k1"))
        assertEquals(slots, reconcile(slots, emptyMap(), agents).slots)
        assertEquals(slots, reconcile(slots, lists(loading = true), agents).slots)
        assertEquals(slots, reconcile(slots, lists(error = "boom"), agents).slots)
        assertTrue(reconcile(slots, lists(error = "boom"), agents).disable.isEmpty())
    }

    @Test fun goneAgent_removesItsSlots_onlyWhenTheAgentListIsKnown() {
        val slots = listOf(ShortcutSlot("f1", "ghost", ShortcutDestination.Folder("/w/app")))
        assertEquals(mapOf("f1" to DisableReason.AGENT_GONE), reconcile(slots, emptyMap(), agents).disable)
        assertEquals(slots, reconcile(slots, emptyMap(), agents.copy(loading = true)).slots)
        assertEquals(slots, reconcile(slots, emptyMap(), agents.copy(error = "x")).slots)
        assertEquals(slots, reconcile(slots, emptyMap(), AgentsState(emptyList(), loading = false)).slots)
    }

    @Test fun homeAndFolder_areNotJudgedBySessions() {
        val slots = listOf(
            ShortcutSlot("h", "kino", ShortcutDestination.Home("agent:kino:main")),
            ShortcutSlot("f", "kino", ShortcutDestination.Folder(null)),
        )
        val r = reconcile(slots, mapOf("kino" to SessionListState(loading = false)), agents)
        assertEquals(slots, r.slots)
        assertTrue(r.disable.isEmpty())
    }

    @Test fun label_prefersCustom_thenDestination() {
        val texts = LabelTexts("新しいチャット")
        val l = lists(active = listOf(session("k1", title = "設計の相談"), session("k2", title = null)))
        val kino = agent("kino", "キノ")
        assertEquals("キノに聞く", shortcutLabel(ShortcutSlot("a", "kino", ShortcutDestination.Home("h"), customLabel = " キノに聞く "), kino, l, texts))
        assertEquals("キノ", shortcutLabel(ShortcutSlot("a", "kino", ShortcutDestination.Home("h")), kino, l, texts))
        assertEquals("clawdroid", shortcutLabel(ShortcutSlot("a", "kino", ShortcutDestination.Folder("/home/mi/Dev/clawdroid/")), kino, l, texts))
        assertEquals("キノ", shortcutLabel(ShortcutSlot("a", "kino", ShortcutDestination.Folder(null)), kino, l, texts))
        assertEquals("設計の相談", shortcutLabel(slot("a", "k1"), kino, l, texts))
        assertEquals("新しいチャット", shortcutLabel(slot("a", "k2"), kino, l, texts))
        // 一覧を読んでいないあいだは前の名前
        assertEquals("前の名前", shortcutLabel(slot("a", "k1"), kino, emptyMap(), texts, fallback = "前の名前"))
    }

    @Test fun published_skipsArchived_andOpensTheRightTarget() {
        val slots = listOf(
            ShortcutSlot("h", "kino", ShortcutDestination.Home("agent:kino:main")),
            slot("s", "k1", archived = true),
            ShortcutSlot("f", "moon", ShortcutDestination.Folder("/w/clawdroid")),
        )
        val out = published(slots, agents.agents, emptyMap(), LabelTexts("新しいチャット"))
        assertEquals(listOf("h", "f"), out.map { it.id })
        assertEquals(listOf(0, 1), out.map { it.rank })
        assertEquals(OpenTarget.Session("kino", "agent:kino:main"), out[0].target)
        assertEquals(OpenTarget.NewChat("moon", "/w/clawdroid"), out[1].target)
    }

    @Test fun upsert_reorder_remove() {
        val a = ShortcutSlot("a", "kino", ShortcutDestination.Home("h"))
        val b = ShortcutSlot("b", "kino", ShortcutDestination.Folder(null))
        var cfg = ShortcutSettings().upsert(a, "wss://gw").upsert(b, "wss://other")
        assertEquals("wss://gw", cfg.gatewayUrl)
        assertEquals(listOf("a", "b"), cfg.slots.map { it.id })
        cfg = cfg.reordered(listOf("b", "a"))
        assertEquals(listOf("b", "a"), cfg.slots.map { it.id })
        cfg = cfg.upsert(a.copy(customLabel = "名前"), null)
        assertEquals("名前", cfg.slots[1].customLabel)
        // 上限を超えて足さない
        val full = (1..MAX_SHORTCUTS).fold(ShortcutSettings()) { acc, i -> acc.upsert(a.copy(id = "x$i"), null) }
        assertEquals(full, full.upsert(a.copy(id = "over"), null))
        assertEquals(listOf("a"), cfg.removed("b").slots.map { it.id })
    }

    @Test fun upsert_keepsArchivedOnlyWhenTheDestinationIsTheSame() {
        val s = slot("s", "k1", archived = true)
        val cfg = ShortcutSettings(slots = listOf(s))
        assertTrue(cfg.upsert(s.copy(customLabel = "x", archived = false), null).slots.single().archived)
        assertEquals(false, cfg.upsert(slot("s", "k2"), null).slots.single().archived)
    }

    @Test fun settings_roundTripThroughJson() {
        val json = Json { ignoreUnknownKeys = true }
        val cfg = ShortcutSettings(
            gatewayUrl = "wss://gw",
            slots = listOf(
                ShortcutSlot("h", "kino", ShortcutDestination.Home("agent:kino:main"), customLabel = "キノに聞く"),
                ShortcutSlot("f", "moon", ShortcutDestination.Folder(null)),
                slot("s", "k1", archived = true),
            ),
        )
        assertEquals(cfg, json.decodeFromString<ShortcutSettings>(json.encodeToString(cfg)))
        val withUnknown = """{"gatewayUrl":null,"slots":[{"id":"f","agentId":"moon","destination":{"type":"folder","cwd":"/w","extra":1},"future":true}]}"""
        val read = json.decodeFromString<ShortcutSettings>(withUnknown)
        assertNull(read.gatewayUrl)
        assertEquals(ShortcutDestination.Folder("/w"), read.slots.single().destination)
    }
}
