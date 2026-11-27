package jp.miyavi.android.sumahoclaw.feature.chat

/** Keep all transcript rows, even when the Gateway repeats an entry ID. */
internal fun chatRowKeys(ids: List<String>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return ids.map { id ->
        val occurrence = seen.getOrDefault(id, 0)
        seen[id] = occurrence + 1
        "message:$occurrence:$id"
    }
}
