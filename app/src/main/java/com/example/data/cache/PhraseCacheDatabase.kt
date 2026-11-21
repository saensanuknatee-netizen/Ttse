package com.example.data.cache

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ai_phrase_cache")
data class PhraseCacheEntity(
    @PrimaryKey val cacheKey: String,
    val rawText: String,
    val rewrittenText: String,
    val rewrittenSsml: String,
    val voiceId: String,
    val styleId: String,
    val aiModeId: String,
    val languageTag: String,
    val particleApplied: Boolean,
    val hitCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
)

@Dao
interface PhraseCacheDao {
    @Query("SELECT * FROM ai_phrase_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): PhraseCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PhraseCacheEntity)

    @Query("UPDATE ai_phrase_cache SET hitCount = hitCount + 1, lastUsedAt = :timestamp WHERE cacheKey = :cacheKey")
    suspend fun recordHit(cacheKey: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM ai_phrase_cache")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM ai_phrase_cache ORDER BY lastUsedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<PhraseCacheEntity>>

    @Query("DELETE FROM ai_phrase_cache")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM ai_phrase_cache WHERE cacheKey IN (
            SELECT cacheKey FROM ai_phrase_cache ORDER BY lastUsedAt ASC LIMIT :deleteCount
        )
        """
    )
    suspend fun evictOldest(deleteCount: Int)

    @Query("SELECT COUNT(*) FROM ai_phrase_cache")
    suspend fun getCount(): Int
}

@Database(entities = [PhraseCacheEntity::class], version = 1, exportSchema = false)
abstract class AuraNavDatabase : RoomDatabase() {
    abstract fun phraseCacheDao(): PhraseCacheDao

    companion object {
        @Volatile
        private var instance: AuraNavDatabase? = null

        fun getInstance(context: Context): AuraNavDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraNavDatabase::class.java,
                    "auranav_phrase_cache.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
