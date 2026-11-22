package jp.miyavi.android.clawdroid.feature.chat

internal fun mergeRestoredDraft(restored: String, existing: String): String =
    if (existing.isBlank()) restored else "$restored\n\n$existing"
