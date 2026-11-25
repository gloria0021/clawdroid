package jp.miyavi.android.clawdroid.core.notify

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 通知・ショートカットの行き先の書き読み（spec 006 R7・spec 007 R2）。 */
@RunWith(RobolectricTestRunner::class)
class OpenIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun roundTrip(target: OpenTarget?) = OpenIntents.targetOf(OpenIntents.intent(context, target))

    @Test fun everyTarget_roundTrips() {
        listOf(
            OpenTarget.Session("kino", "agent:kino:main"),
            OpenTarget.NewChat("moon", "/w/clawdroid"),
            OpenTarget.NewChat("moon", null),
            OpenTarget.Automation("job-1"),
            OpenTarget.Approvals,
        ).forEach { assertEquals(it, roundTrip(it)) }
        assertNull(roundTrip(null))
    }

    @Test fun missingExtras_openTheAppOnly() {
        assertNull(OpenIntents.targetOf(null))
        assertNull(OpenIntents.targetOf(Intent().putExtra(OpenIntents.EXTRA_OPEN, OpenIntents.OPEN_SESSION).putExtra(OpenIntents.EXTRA_AGENT, "kino")))
        assertNull(OpenIntents.targetOf(Intent().putExtra(OpenIntents.EXTRA_OPEN, OpenIntents.OPEN_NEW_CHAT)))
        assertNull(OpenIntents.targetOf(Intent().putExtra(OpenIntents.EXTRA_OPEN, "unknown")))
    }

    @Test fun shortcutId_isReadBack() {
        val intent = OpenIntents.intent(context, OpenTarget.Approvals).putExtra(OpenIntents.EXTRA_SHORTCUT_ID, "s1")
        assertEquals("s1", OpenIntents.shortcutIdOf(intent))
        assertNull(OpenIntents.shortcutIdOf(Intent()))
    }
}
