package jp.miyavi.android.sumahoclaw.feature.chat.sidebar

import org.junit.Assert.assertEquals
import org.junit.Test

class OfficeAvatarTest {
    @Test
    fun chosenWins() = assertEquals(2, officeAvatarOf("main", mapOf("main" to 2)))

    @Test
    fun outOfRangeFallsBackToDefault() = assertEquals(officeAvatarOf("main", emptyMap()), officeAvatarOf("main", mapOf("main" to 9)))

    @Test
    fun defaultMatchesThePrototype() {
        // 試作（JS）の hashId: h = (h * 31 + 文字) | 0、Math.abs(h) % 4
        fun js(id: String): Int { var h = 0; id.codePoints().forEach { h = h * 31 + it }; return (Math.abs(h.toLong()) % 4).toInt() }
        for (id in listOf("main", "architect", "wordsmith", "cj", "a1", "日本語のエージェント", "😀x")) assertEquals(id, js(id), officeAvatarOf(id, emptyMap()))
    }
}
