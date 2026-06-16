package ie.adrianszydlo.navitunes.data.offline

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

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val profileId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArt: String?,
    val duration: Int,
    val filePath: String,
    val sizeBytes: Long,
    val status: String,       // "queued" | "downloading" | "completed" | "failed"
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observe(profileId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE songId = :songId AND profileId = :profileId LIMIT 1")
    suspend fun bySongId(songId: String, profileId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = :status AND profileId = :profileId")
    suspend fun byStatus(status: String, profileId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE profileId = :profileId")
    suspend fun allForProfile(profileId: String): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE songId = :songId AND profileId = :profileId")
    suspend fun delete(songId: String, profileId: String)

    @Query("DELETE FROM downloads WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)

    @Query("UPDATE downloads SET status = :status, errorMessage = :err WHERE songId = :songId AND profileId = :profileId")
    suspend fun updateStatus(songId: String, profileId: String, status: String, err: String? = null)
}

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class DownloadDb : RoomDatabase() {
    abstract fun dao(): DownloadDao

    companion object {
        fun create(context: Context): DownloadDb = Room.databaseBuilder(
            context.applicationContext,
            DownloadDb::class.java,
            "navitunes_downloads.db"
        ).build()
    }
}
