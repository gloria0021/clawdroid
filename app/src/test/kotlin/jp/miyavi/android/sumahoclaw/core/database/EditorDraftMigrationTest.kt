package jp.miyavi.android.sumahoclaw.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** 版 2 → 3（spec 008: file_drafts に書式の列を足した）で、更新の前に書いた下書きを失わない。 */
@RunWith(RobolectricTestRunner::class)
class EditorDraftMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun version2Draft_survivesWithDefaults() {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        // 版 2 のデータベースを、書き出したスキーマのとおりに作る
        val schema = JSONObject(File("schemas/jp.miyavi.android.sumahoclaw.core.database.SumahoClawDatabase/2.json").readText()).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.execSQL("INSERT INTO file_drafts (`key`, content, baseHash, cursor, updatedAt) VALUES ('session:s:a.kt', 'draft', 'h1', 3, 0)")
        db.version = 2
        db.close()

        val room = Room.databaseBuilder(context, SumahoClawDatabase::class.java, name).allowMainThreadQueries().build()
        val draft = runBlocking { room.fileDrafts().get("session:s:a.kt") }!!
        assertEquals("draft", draft.content)
        assertEquals("UTF_8", draft.charset)
        assertFalse(draft.bom)
        assertEquals("LF", draft.lineSeparator)
        room.close()
        context.deleteDatabase(name)
    }
}
