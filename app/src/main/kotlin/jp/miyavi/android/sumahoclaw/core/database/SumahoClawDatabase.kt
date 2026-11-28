package jp.miyavi.android.sumahoclaw.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** 入力中の下書き（FR-028）。新規セッションは `new:<agentId>` を key にする。 */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val sessionKey: String,
    val text: String,
    val updatedAt: Long,
)

/** 読んでいた位置（FR-028、SC-008）。 */
@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val sessionKey: String,
    val anchorMessageId: String,
    val offsetPx: Int,
    /** 一番下にいたか（いたなら新しい応答に追従する）。 */
    val atBottom: Boolean,
)

/** 保存していないファイルの変更（spec 002 FR-011）。key は `session:<sessionKey>:<path>`。 */
@Entity(tableName = "file_drafts")
data class FileDraftEntity(
    @PrimaryKey val key: String,
    val content: String,
    /** 編集を始めたときの Gateway のハッシュ（衝突の確認に使う）。 */
    val baseHash: String,
    val cursor: Int,
    val updatedAt: Long,
    /** 文字コード（spec 008。`TextCharset` の名前）。 */
    @ColumnInfo(defaultValue = "UTF_8") val charset: String = "UTF_8",
    @ColumnInfo(defaultValue = "0") val bom: Boolean = false,
    /** 改行（`LineSeparator` の名前）。 */
    @ColumnInfo(defaultValue = "LF") val lineSeparator: String = "LF",
)

@Dao
interface FileDraftDao {
    @Query("SELECT * FROM file_drafts WHERE `key` = :key")
    suspend fun get(key: String): FileDraftEntity?

    @Upsert
    suspend fun upsert(draft: FileDraftEntity)

    @Query("DELETE FROM file_drafts WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE sessionKey = :key")
    suspend fun get(key: String): DraftEntity?

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE sessionKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface ReadingPositionDao {
    @Query("SELECT * FROM reading_positions WHERE sessionKey = :key")
    suspend fun get(key: String): ReadingPositionEntity?

    @Upsert
    suspend fun upsert(position: ReadingPositionEntity)
}

@Database(
    entities = [DraftEntity::class, ReadingPositionEntity::class, FileDraftEntity::class],
    version = 4,
    exportSchema = true,
    // 版 2: file_drafts を追加しただけ。版 3: file_drafts に書式の列を足しただけ（spec 008）。
    // 版 4: 中身は版 3 と同じ。開発中の版 3 に savedUri の列があったもの（試しに配った APK）を、列を外した形に作り直す（MIGRATION_3_4）
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class SumahoClawDatabase : RoomDatabase() {
    abstract fun drafts(): DraftDao
    abstract fun readingPositions(): ReadingPositionDao
    abstract fun fileDrafts(): FileDraftDao

    companion object {
        fun create(context: Context): SumahoClawDatabase =
            Room.databaseBuilder(context, SumahoClawDatabase::class.java, "sumahoclaw.db").addMigrations(MIGRATION_3_4).build()

        /**
         * 版 3 → 4。版 3 には 2 つの形がある（savedUri の列があるもの・無いもの）ので、どちらからでも
         * file_drafts を今の形で作り直し、共通の列を写す（下書きを失わない）。
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `file_drafts_new` (`key` TEXT NOT NULL, `content` TEXT NOT NULL, `baseHash` TEXT NOT NULL, " +
                        "`cursor` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `charset` TEXT NOT NULL DEFAULT 'UTF_8', " +
                        "`bom` INTEGER NOT NULL DEFAULT 0, `lineSeparator` TEXT NOT NULL DEFAULT 'LF', PRIMARY KEY(`key`))",
                )
                db.execSQL(
                    "INSERT INTO `file_drafts_new` (`key`, `content`, `baseHash`, `cursor`, `updatedAt`, `charset`, `bom`, `lineSeparator`) " +
                        "SELECT `key`, `content`, `baseHash`, `cursor`, `updatedAt`, `charset`, `bom`, `lineSeparator` FROM `file_drafts`",
                )
                db.execSQL("DROP TABLE `file_drafts`")
                db.execSQL("ALTER TABLE `file_drafts_new` RENAME TO `file_drafts`")
            }
        }
    }
}
