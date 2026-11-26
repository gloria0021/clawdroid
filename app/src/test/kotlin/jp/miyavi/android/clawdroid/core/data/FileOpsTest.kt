package jp.miyavi.android.clawdroid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileOpsTest {
    @Test
    fun validateName_rejectsUnsafeNames() {
        assertEquals(NameError.EMPTY, validateName(" "))
        assertEquals(NameError.SLASH, validateName("a/b"))
        assertEquals(NameError.SLASH, validateName("../x"))
        assertEquals(NameError.DOT, validateName(".."))
        assertEquals(NameError.CONTROL, validateName("a\nb"))
        assertEquals(NameError.TOO_LONG, validateName("あ".repeat(86)))
        assertEquals(NameError.EXISTS, validateName("a.txt", listOf("a.txt")))
        assertNull(validateName(".env"))
        assertNull(validateName("it's.md"))
    }

    @Test
    fun secretNames() {
        assertTrue(isSecretName(".env"))
        assertTrue(isSecretName("app/.env.local"))
        assertTrue(isSecretName("server.PEM"))
        assertTrue(isSecretName("client_secret.json"))
        assertFalse(isSecretName("README.md"))
        assertFalse(isSecretName("environment.kt"))
    }

    @Test
    fun parse_ignoresEchoedCommand_andReadsCode() {
        val nonce = "abc123"
        val echoed = fileOpCommand(FileOp.Delete("x", false), "/w", nonce)
        assertNull(parseFileOpOutput(echoed, nonce))
        assertEquals(FileOpResult.Done, parseFileOpOutput(echoed + "\u001eabc123:0\u001e\r\n", nonce))
        assertEquals(FileOpResult.AlreadyExists, parseFileOpOutput("\u001eabc123:2\u001e", nonce))
        assertEquals(FileOpResult.OutsideRoot, parseFileOpOutput("\u001eabc123:3\u001e", nonce))
        assertEquals(FileOpResult.NotFound, parseFileOpOutput("\u001eabc123:5\u001e", nonce))
        assertEquals(
            FileOpResult.Failed("rm: cannot remove 'a': Permission denied"),
            parseFileOpOutput("\u001b[31mrm: cannot remove 'a': Permission denied\u001b[0m\r\n\u001eabc123:1\u001e", nonce),
        )
        // 別の nonce の印は無視する
        assertNull(parseFileOpOutput("\u001eother:0\u001e", nonce))
    }

    /** 組み立てた入力を本物の sh に流して、作業フォルダの中だけで動くことを確かめる（sh の無い環境では飛ばす）。 */
    @Test
    fun command_runsInRealShell() {
        assumeTrue(File("/bin/sh").canExecute())
        val base = Files.createTempDirectory("fileops").toFile()
        val root = File(base, "root").apply { mkdirs() }
        File(base, "outside").mkdirs()
        Files.createSymbolicLink(File(root, "link").toPath(), File(base, "outside").toPath())
        fun run(op: FileOp): FileOpResult? {
            val input = fileOpCommand(op, root.path, "n0nce").replace("\r", "\n").replace("stty -echo; ", "")
            val process = ProcessBuilder("/bin/sh").redirectErrorStream(true).start()
            process.outputStream.use { it.write(input.toByteArray()) }
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            return parseFileOpOutput(out, "n0nce")
        }
        try {
            assertEquals(FileOpResult.Done, run(FileOp.CreateFile("", "it's.txt")))
            assertTrue(File(root, "it's.txt").exists())
            assertEquals(FileOpResult.AlreadyExists, run(FileOp.CreateFile("", "it's.txt")))
            assertEquals(FileOpResult.OutsideRoot, run(FileOp.CreateFile("link", "x")))
            assertEquals(FileOpResult.Done, run(FileOp.CreateFolder("", "d")))
            assertEquals(FileOpResult.Done, run(FileOp.Rename("it's.txt", "b.txt")))
            assertTrue(File(root, "b.txt").exists())
            assertEquals(FileOpResult.OutsideRoot, run(FileOp.Delete(".", true)))
            assertEquals(FileOpResult.Done, run(FileOp.Delete("d", true)))
            assertEquals(FileOpResult.NotFound, run(FileOp.Delete("nope", false)))
            assertEquals(FileOpResult.Done, run(FileOp.Delete("link", false)))
            assertTrue(File(base, "outside").exists())
            // アップロード: 一時置き場から置く。同じ名前は上書きを選んだときだけ、フォルダは置き換えない
            fun staged(text: String) = File.createTempFile("upload", ".bin", base).apply { writeText(text) }.path
            assertEquals(FileOpResult.Done, run(FileOp.Place("", "up load.txt", staged("one"), overwrite = false)))
            assertEquals("one", File(root, "up load.txt").readText())
            assertEquals(FileOpResult.AlreadyExists, run(FileOp.Place("", "up load.txt", staged("two"), overwrite = false)))
            assertEquals(FileOpResult.Done, run(FileOp.Place("", "up load.txt", staged("two"), overwrite = true)))
            assertEquals("two", File(root, "up load.txt").readText())
            assertEquals(FileOpResult.Done, run(FileOp.CreateFolder("", "dir")))
            assertEquals(FileOpResult.AlreadyExists, run(FileOp.Place("", "dir", staged("x"), overwrite = true)))
            Files.createSymbolicLink(File(root, "link2").toPath(), File(base, "outside").toPath())
            assertEquals(FileOpResult.OutsideRoot, run(FileOp.Place("link2", "x", staged("x"), overwrite = false)))
            assertEquals(FileOpResult.NotFound, run(FileOp.Place("", "y", File(base, "missing").path, overwrite = false)))
            assertTrue(root.listFiles()!!.none { it.name.startsWith(".clawdroid-upload") })
        } finally {
            base.deleteRecursively()
        }
    }
}
