package jp.miyavi.android.sumahoclaw.feature.editor

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import jp.miyavi.android.sumahoclaw.core.data.FileRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** エディターの開き方（spec 008 contracts/editor-intents.md）。 */
@RunWith(RobolectricTestRunner::class)
class EditorIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun roundTrip(launch: EditorLaunch) = EditorIntents.read(EditorIntents.intent(context, launch), fromOutside = false)

    @Test fun appTargets_roundTrip() {
        listOf(
            EditorLaunch(EditTarget.GatewayFile(FileRoot.Session("agent:a:s1", "a"), "src/a.kt"), handoff = "h", line = 42),
            EditorLaunch(EditTarget.GatewayFile(FileRoot.AgentFile("a"), "AGENTS.md")),
            EditorLaunch(EditTarget.Attachment("media://inbound/x.md", "m1", "agent:a:s1", "a", "x.md", "text/markdown")),
            EditorLaunch(EditTarget.Attachment("media://inbound/y.txt", "m2", "agent:a:s1", null, "y.txt", null)),
            EditorLaunch(EditTarget.PastedText("agent:a:s1", "t1")),
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test fun outsideIntent_onlyOpensContentUris() {
        val uri = Uri.parse("content://com.example/doc/1")
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain")
        assertEquals(EditTarget.LocalDocument(uri), EditorIntents.read(view, fromOutside = true)?.target)
        assertNull(EditorIntents.read(Intent(Intent.ACTION_VIEW).setData(Uri.parse("file:///sdcard/a.txt")), fromOutside = true))
        assertNull(EditorIntents.read(Intent(Intent.ACTION_SEND).setData(uri), fromOutside = true))
    }

    @Test fun outsideIntent_cannotOpenGatewayFiles() {
        // ほかのアプリがアプリの中の extras を真似しても、Gateway のファイルは開けない
        val forged = EditorIntents.write(Intent(), EditorLaunch(EditTarget.GatewayFile(FileRoot.Session("agent:a:s1", "a"), "secret.txt")))
        assertNull(EditorIntents.read(forged, fromOutside = true))
    }

    @Test fun missingExtras_areRejected() {
        assertNull(EditorIntents.read(Intent(), fromOutside = false))
        assertNull(EditorIntents.read(Intent().putExtra("editor.kind", "session"), fromOutside = false))
    }
}
