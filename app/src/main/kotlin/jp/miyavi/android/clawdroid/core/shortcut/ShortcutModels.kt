package jp.miyavi.android.clawdroid.core.shortcut

import jp.miyavi.android.clawdroid.core.notify.OpenTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** ランチャーの長押しメニューに出す枠の数の上限（多くのランチャーが出せる数。spec 007 FR-005）。 */
const val MAX_SHORTCUTS = 4

/** 枠の行き先（spec 007 FR-002、data-model.md）。 */
@Serializable
sealed interface ShortcutDestination {
    /** エージェントのホーム。押したときに一覧を待たないよう、作るときにホームの会話のキーを覚える（R3）。 */
    @Serializable @SerialName("home")
    data class Home(val sessionKey: String) : ShortcutDestination

    /** 作業フォルダで新しい会話（[cwd] が null ならエージェントの既定のフォルダ）。 */
    @Serializable @SerialName("folder")
    data class Folder(val cwd: String?) : ShortcutDestination

    /** 既存のセッション。 */
    @Serializable @SerialName("session")
    data class Session(val sessionKey: String) : ShortcutDestination
}

/** 設定に保存する枠 1 つ。 */
@Serializable
data class ShortcutSlot(
    /** ショートカットの ID。並べ替え・名前の変更では変えない。 */
    val id: String,
    val agentId: String,
    val destination: ShortcutDestination,
    /** マスターが付けた名前。無ければ行き先から作る（R6）。 */
    val customLabel: String? = null,
    /** 行き先がアーカイブ済み。ランチャーには出さず、戻されたら復活する（FR-023）。 */
    val archived: Boolean = false,
) {
    /** 押したときに開く行き先。 */
    val target: OpenTarget
        get() = when (destination) {
            is ShortcutDestination.Home -> OpenTarget.Session(agentId, destination.sessionKey)
            is ShortcutDestination.Session -> OpenTarget.Session(agentId, destination.sessionKey)
            is ShortcutDestination.Folder -> OpenTarget.NewChat(agentId, destination.cwd)
        }

    /** 片付けの対象になるセッションのキー（ホームはアーカイブできないので対象外）。 */
    val watchedSessionKey: String?
        get() = (destination as? ShortcutDestination.Session)?.sessionKey
}

/** 保存の単位（`SettingsStore.shortcuts`）。 */
@Serializable
data class ShortcutSettings(
    /** 枠を作ったときの接続先。違う接続先につながったら枠を全部消す（R7）。 */
    val gatewayUrl: String? = null,
    val slots: List<ShortcutSlot> = emptyList(),
)

/**
 * 枠を足す・直す（設定の画面から）。同じ ID があれば置き換え、無ければ上限まで後ろに足す。
 * 行き先を変えたら、アーカイブ済みの印は外す（新しい行き先で判定し直す）。接続先をまだ覚えていなければ [gatewayUrl] を覚える。
 */
fun ShortcutSettings.upsert(slot: ShortcutSlot, gatewayUrl: String?): ShortcutSettings {
    val index = slots.indexOfFirst { it.id == slot.id }
    val next = if (index >= 0) {
        val old = slots[index]
        slots.toMutableList().apply { set(index, slot.copy(archived = old.archived && old.destination == slot.destination && old.agentId == slot.agentId)) }
    } else if (slots.size < MAX_SHORTCUTS) {
        slots + slot.copy(archived = false)
    } else {
        return this
    }
    return copy(gatewayUrl = this.gatewayUrl ?: gatewayUrl, slots = next)
}

fun ShortcutSettings.removed(id: String): ShortcutSettings = copy(slots = slots.filterNot { it.id == id })

/** [ids] の並びにする（[ids] に無い枠は後ろに残す）。 */
fun ShortcutSettings.reordered(ids: List<String>): ShortcutSettings {
    val rank = ids.withIndex().associate { (i, id) -> id to i }
    return copy(slots = slots.withIndex().sortedBy { rank[it.value.id] ?: (ids.size + it.index) }.map { it.value })
}
