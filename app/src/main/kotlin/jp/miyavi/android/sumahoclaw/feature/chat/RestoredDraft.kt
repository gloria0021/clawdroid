package jp.miyavi.android.sumahoclaw.feature.chat

internal fun mergeRestoredDraft(restored: String, existing: String): String =
    if (existing.isBlank()) restored else "$restored\n\n$existing"
