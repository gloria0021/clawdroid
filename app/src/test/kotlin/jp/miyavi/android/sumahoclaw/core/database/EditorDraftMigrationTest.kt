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

/** 版 2 → 3 → 4（spec 008: file_drafts に書式の列を足した）と、開発中の版 3（savedUri の列あり）→ 4 で、下書きを失わず開ける。 */
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

        val room = Room.databaseBuilder(context, SumahoClawDatabase::class.java, name).addMigrations(SumahoClawDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        val draft = runBlocking { room.fileDrafts().get("session:s:a.kt") }!!
        assertEquals("draft", draft.content)
        assertEquals("UTF_8", draft.charset)
        assertFalse(draft.bom)
        assertEquals("LF", draft.lineSeparator)
        room.close()
        context.deleteDatabase(name)
    }

    /** 試しに配った APK の版 3（savedUri の列あり）から更新しても落ちない（2026-11-28 の起動時の落ち）。 */
    @Test fun devVersion3WithSavedUri_opens() {
        val name = "migration-dev3.db"
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        val v2 = JSONObject(File("schemas/jp.miyavi.android.sumahoclaw.core.database.SumahoClawDatabase/2.json").readText()).getJSONObject("database")
        val entities = v2.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            if (e.getString("tableName") != "file_drafts") db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
        }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `file_drafts` (`key` TEXT NOT NULL, `content` TEXT NOT NULL, `baseHash` TEXT NOT NULL, `cursor` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `charset` TEXT NOT NULL DEFAULT 'UTF_8', `bom` INTEGER NOT NULL DEFAULT 0, " +
                "`lineSeparator` TEXT NOT NULL DEFAULT 'LF', `savedUri` TEXT, PRIMARY KEY(`key`))",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '4fb32f9c9450ac64b6e4adf2539b4804')")
        db.execSQL("INSERT INTO file_drafts (`key`, content, baseHash, cursor, updatedAt, charset, bom, lineSeparator, savedUri) VALUES ('uri:x', 'body', 'h', 1, 0, 'SHIFT_JIS', 1, 'CRLF', 'content://d/1')")
        db.version = 3
        db.close()

        val room = Room.databaseBuilder(context, SumahoClawDatabase::class.java, name).addMigrations(SumahoClawDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        val draft = runBlocking { room.fileDrafts().get("uri:x") }!!
        assertEquals("body", draft.content)
        assertEquals("SHIFT_JIS", draft.charset)
        assertEquals("CRLF", draft.lineSeparator)
        room.close()
        context.deleteDatabase(name)
    }
}
