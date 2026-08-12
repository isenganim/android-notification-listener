package com.saquone.notificationlistener.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Unique index `(pkg, text, postedAt)` mencegah notifikasi masuk dua kali saat
 * `onListenerConnected` menyapu ulang shade di tiap rebind. Bergantung pada [postedAt] =
 * `StatusBarNotification.postTime`; kalau diganti waktu proses, dedup ikut mati.
 */
@Entity(tableName = "event", indices = [Index(value = ["pkg", "text", "postedAt"], unique = true)])
data class Event(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val pkg: String,
  val title: String,
  val text: String,
  val postedAt: Long,
  val status: String = PENDING,
  val attempts: Int = 0,
  val lastError: String? = null,
) {
  companion object {
    const val PENDING = "pending"
    const val SENT = "sent"
    const val FAILED = "failed"
  }
}

@Dao
interface EventDao {
  /** `IGNORE`: duplikat harus jadi no-op, bukan exception di dalam listener. */
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: Event): Long

  @Query("SELECT * FROM event WHERE status IN ('pending','failed') ORDER BY postedAt ASC LIMIT 50")
  suspend fun pending(): List<Event>

  @Query("UPDATE event SET status='sent', lastError=NULL WHERE id=:id") suspend fun markSent(id: Long)

  @Query("UPDATE event SET status='failed', attempts=attempts+1, lastError=:error WHERE id=:id")
  suspend fun markFailed(id: Long, error: String)

  @Query("SELECT * FROM event ORDER BY postedAt DESC LIMIT 100") fun recent(): Flow<List<Event>>

  @Query("SELECT count(*) FROM event WHERE status IN ('pending','failed')") fun pendingCount(): Flow<Int>

  @Query("DELETE FROM event WHERE status='sent' AND postedAt < :before") suspend fun purgeSent(before: Long)

  @Query("DELETE FROM event") suspend fun clear()
}

@Database(entities = [Event::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
  abstract fun events(): EventDao

  companion object {
    fun open(context: Context): Db = Room.databaseBuilder(context, Db::class.java, "listener.db").build()
  }
}
