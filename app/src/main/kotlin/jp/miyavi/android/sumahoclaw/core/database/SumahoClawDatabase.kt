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
    version = 3,
    exportSchema = true,
    // 版 2: file_drafts を追加しただけ。版 3: file_drafts に書式の列を足しただけ（spec 008）
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class SumahoClawDatabase : RoomDatabase() {
    abstract fun drafts(): DraftDao
    abstract fun readingPositions(): ReadingPositionDao
    abstract fun fileDrafts(): FileDraftDao

    companion object {
        fun create(context: Context): SumahoClawDatabase =
            Room.databaseBuilder(context, SumahoClawDatabase::class.java, "sumahoclaw.db").build()
    }
}
