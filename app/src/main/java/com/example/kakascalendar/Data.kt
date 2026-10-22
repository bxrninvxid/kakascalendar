package com.example.kakascalendar

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: Int,              // Bristol stool scale 1..7
    val comment: String = "",
    val tags: String = ""       // comma-separated
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    fun all(): Flow<List<Entry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entry: Entry)
    @Delete suspend fun delete(entry: Entry)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Entry::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): EntryDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "kakascalendar.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
