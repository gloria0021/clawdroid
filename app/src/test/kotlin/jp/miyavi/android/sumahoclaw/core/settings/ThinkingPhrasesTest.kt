package jp.miyavi.android.sumahoclaw.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingPhrasesTest {
    @Test fun oneLineIsOnePhrase() {
        val text = "スタックトレースを遡上中…\n\n  今いいところです  \nコーヒーをトークンに変換中...\n"
        assertEquals(listOf("スタックトレースを遡上中", "今いいところです", "コーヒーをトークンに変換中"), ThinkingPhrases.parse(text))
    }

    @Test fun keepsEllipsisInTheMiddle() {
        assertEquals("あと少し…たぶん", ThinkingPhrases.normalize("あと少し…たぶん…"))
        assertEquals("", ThinkingPhrases.normalize("……"))
    }

    @Test fun formatRoundTrips() {
        val phrases = listOf("脳内StackOverflowに質問中", "点と点をつないでいます")
        assertEquals(phrases, ThinkingPhrases.parse(ThinkingPhrases.format(phrases)))
        assertEquals("脳内StackOverflowに質問中\n点と点をつないでいます", ThinkingPhrases.format(phrases.map { "$it…" }))
    }
}
