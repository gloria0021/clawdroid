package jp.miyavi.android.clawdroid.core.database

import android.content.Context
import androidx.room.Dao
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

@Database(entities = [DraftEntity::class, ReadingPositionEntity::class], version = 1, exportSchema = true)
abstract class ClawdroidDatabase : RoomDatabase() {
    abstract fun drafts(): DraftDao
    abstract fun readingPositions(): ReadingPositionDao

    companion object {
        fun create(context: Context): ClawdroidDatabase =
            Room.databaseBuilder(context, ClawdroidDatabase::class.java, "clawdroid.db").build()
    }
}
