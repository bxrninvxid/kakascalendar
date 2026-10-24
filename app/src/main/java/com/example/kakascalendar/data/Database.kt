package com.example.kakascalendar.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: Int,                                       // Bristol stool scale 1..7
    val comment: String = "",
    val tags: String = "",                               // comma-separated
    @ColumnInfo(name = "duration_sec") val durationSec: Int = 0,
    val rating: Int = 0                                  // 0 = not rated, 1..5
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    fun all(): Flow<List<Entry>>

    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    suspend fun allOnce(): List<Entry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: Entry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<Entry>)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("DELETE FROM entries")
    suspend fun clear()
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN duration_sec INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE entries ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [Entry::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): EntryDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "kakascalendar.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
