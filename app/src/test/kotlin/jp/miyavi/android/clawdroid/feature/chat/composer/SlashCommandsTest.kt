package jp.miyavi.android.clawdroid.feature.chat.composer

import jp.miyavi.android.clawdroid.core.data.Parse
import jp.miyavi.android.clawdroid.core.data.SettingChoice
import jp.miyavi.android.clawdroid.core.data.SlashArg
import jp.miyavi.android.clawdroid.core.data.SlashCommand
import jp.miyavi.android.clawdroid.core.gateway.Frames
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandsTest {
    private val think = SlashCommand(
        "think", listOf("t"), "Set thinking level", acceptsArgs = true,
        args = listOf(SlashArg("level", "", listOf(SettingChoice("off", "off"), SettingChoice("low", "low"), SettingChoice("high", "high")))),
    )
    private val status = SlashCommand("status", emptyList(), "Show status", acceptsArgs = false)
    private val model = SlashCommand("model", listOf("m"), "Pick model", acceptsArgs = true)
    private val preview = SlashCommand("artifact_preview", emptyList(), "Preview artifacts", acceptsArgs = true, skillName = "Artifact Preview", skillModelVisible = true)
    private val hidden = SlashCommand("secret_skill", emptyList(), "", acceptsArgs = true, skillName = "Secret", skillModelVisible = false)
    private val commands = listOf(think, status, model, preview, hidden)

    private fun texts(input: String, cursor: Int = input.length) =
        slashSuggestions(input, commands, cursor).map { it.applyTo(input).first }

    @Test fun slashAlone_listsEverything_skillsLast() =
        assertEquals(listOf("/think ", "/status", "/model ", "/artifact_preview ", "/secret_skill "), texts("/"))

    @Test fun dollar_completesVisibleSkillsAnywhere() {
        assertEquals(listOf("${'$'}artifact_preview "), texts("${'$'}"))
        assertEquals(listOf("please ${'$'}artifact_preview "), texts("please ${'$'}art"))
        // 表示名の途中一致（空白は - とみなす）
        assertEquals(listOf("${'$'}artifact_preview "), texts("${'$'}preview"))
        // 文の途中なら空白を足さず、カーソルの位置へ入れる
        val input = "${'$'}ar then"
        val picked = slashSuggestions(input, commands, cursor = 3).single().applyTo(input)
        assertEquals("${'$'}artifact_preview then" to "${'$'}artifact_preview".length, picked)
    }

    @Test fun dollar_ignoresPricesAndMidWord() {
        assertTrue(texts("costs ${'$'}5").isEmpty())
        assertTrue(texts("US${'$'}art").isEmpty())
        assertTrue(texts("${'$'}artifact_preview").isEmpty())
    }

    @Test fun prefixBeforeContains() {
        assertEquals(listOf("/status"), texts("/st"))
        assertEquals(listOf("/model "), texts("/mo"))
        // 打った文字で始まるものだけ（/re → /reset など）
        assertEquals(listOf("/think "), texts("/t"))
        // 始まるものが無いときだけ、途中に含むもの（スキルのコマンドは最後）
        assertEquals(listOf("/status"), texts("/tat"))
        assertEquals(listOf("/artifact_preview "), texts("/fact"))
        assertEquals(listOf("/model "), texts("/mo"))
    }

    @Test fun finishedCommandWithoutArgs_hidesPanel() = assertTrue(texts("/status").isEmpty())

    @Test fun argumentChoices_byAliasToo() {
        assertEquals(listOf("/think off", "/think low", "/think high"), texts("/think "))
        assertEquals(listOf("/think high"), texts("/t h"))
        assertTrue(texts("/think high").isEmpty())
        assertTrue(texts("/think high more").isEmpty())
        assertTrue(texts("/model gpt").isEmpty())
    }

    @Test fun notACommand() {
        assertTrue(texts("hello /status").isEmpty())
        assertTrue(texts("/status\nmore").isEmpty())
    }

    @Test fun parse_commandsList() {
        val json = Frames.json.parseToJsonElement(
            """{"commands":[
              {"name":"think","textAliases":["/think","/t"],"description":"Set thinking","scope":"both","acceptsArgs":true,
               "args":[{"name":"level","description":"Level","type":"string","choices":[{"value":"high","label":"High"}]}]},
              {"name":"nativeOnly","description":"x","scope":"native","acceptsArgs":false},
              {"name":"status","textAliases":["/status"],"description":"Status","scope":"text","acceptsArgs":false},
              {"name":"artifact_preview","textAliases":["/artifact_preview"],"description":"x","scope":"text","acceptsArgs":true,"source":"skill","skillDisplayName":"Artifact Preview","skillModelVisible":true}
            ]}""",
        ).jsonObject
        val parsed = Parse.slashCommands(json)
        assertEquals(listOf("think", "status", "artifact_preview"), parsed.map { it.name })
        assertEquals(null, parsed[0].skillName)
        assertEquals("Artifact Preview", parsed[2].skillName)
        assertEquals(listOf("t"), parsed[0].aliases)
        assertEquals(SettingChoice("high", "High"), parsed[0].args.single().choices.single())
    }
}
