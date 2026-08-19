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
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
  /** Nominal hasil pola katalog — untuk ditampilkan; server tetap menghitung ulang sendiri. */
  val amount: Long? = null,
  /** Jawaban server: nominal cocok dengan tagihan pending dan tagihan itu ditandai lunas. */
  val verified: Boolean = false,
  val chargeId: Long? = null,
) {
  companion object {
    const val PENDING = "pending"
    const val SENT = "sent"
    const val FAILED = "failed"
  }
}

/**
 * Katalog gateway hasil sinkron. Room, bukan DataStore: isinya daftar relasional yang di-query
 * per-paket oleh listener. Sifatnya CACHE — sumber kebenarannya `GET /gateways` di qris-server.
 */
@Entity(tableName = "gateway")
data class GatewayEntity(
  @PrimaryKey val key: String,
  val label: String,
  val packages: List<String>,
  val patterns: List<String>,
  val verified: Boolean,
  val syncedAt: Long,
)

class StringListConverter {
  @TypeConverter fun fromList(v: List<String>): String = v.joinToString("\n")

  @TypeConverter fun toList(v: String): List<String> = if (v.isEmpty()) emptyList() else v.split("\n")
}

@Dao
interface EventDao {
  /** `IGNORE`: duplikat harus jadi no-op, bukan exception di dalam listener. */
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: Event): Long

  @Query("SELECT * FROM event WHERE status IN ('pending','failed') ORDER BY postedAt ASC LIMIT 50")
  suspend fun pending(): List<Event>

  @Query("UPDATE event SET status='sent', lastError=NULL, verified=:verified, chargeId=:chargeId WHERE id=:id")
  suspend fun markSent(id: Long, verified: Boolean, chargeId: Long?)

  @Query("UPDATE event SET status='failed', attempts=attempts+1, lastError=:error WHERE id=:id")
  suspend fun markFailed(id: Long, error: String)

  @Query("SELECT * FROM event ORDER BY postedAt DESC LIMIT 100") fun recent(): Flow<List<Event>>

  @Query("SELECT * FROM event ORDER BY postedAt DESC LIMIT :limit") fun recentPaged(limit: Int): Flow<List<Event>>

  @Query("SELECT count(*) FROM event WHERE status IN ('pending','failed')") fun pendingCount(): Flow<Int>

  @Query("DELETE FROM event WHERE status='sent' AND postedAt < :before") suspend fun purgeSent(before: Long)

  @Query("DELETE FROM event WHERE pkg NOT IN (:activePackages)") suspend fun purgeUnmatchedPackages(activePackages: List<String>)

  @Query("DELETE FROM event") suspend fun clear()
}

@Dao
interface GatewayDao {
  @Query("SELECT * FROM gateway ORDER BY label") suspend fun all(): List<GatewayEntity>

  @Query("SELECT * FROM gateway ORDER BY label") fun allFlow(): Flow<List<GatewayEntity>>

  @Query("SELECT count(*) FROM gateway") suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(list: List<GatewayEntity>)

  @Query("DELETE FROM gateway WHERE key NOT IN (:keep)") suspend fun deleteMissing(keep: List<String>)
}

@Database(entities = [Event::class, GatewayEntity::class], version = 3, exportSchema = false)
@TypeConverters(StringListConverter::class)
abstract class Db : RoomDatabase() {
  abstract fun events(): EventDao

  abstract fun gateways(): GatewayDao

  companion object {
    // Migrasi, bukan destructive: antrean berisi notifikasi yang BELUM terkirim — menghapusnya
    // saat update berarti kehilangan pembayaran.
    private val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE event ADD COLUMN amount INTEGER")
        }
      }

    private val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE event ADD COLUMN verified INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE event ADD COLUMN chargeId INTEGER")
          db.execSQL(
            """CREATE TABLE IF NOT EXISTS gateway (
                 `key` TEXT NOT NULL PRIMARY KEY,
                 label TEXT NOT NULL,
                 packages TEXT NOT NULL,
                 patterns TEXT NOT NULL,
                 verified INTEGER NOT NULL,
                 syncedAt INTEGER NOT NULL)"""
          )
        }
      }

    fun open(context: Context): Db =
      Room.databaseBuilder(context, Db::class.java, "listener.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
  }
}
