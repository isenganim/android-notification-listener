package com.saquone.notificationlistener.util

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay

/**
 * Membuktikan listener benar-benar ter-bind, bukan sekadar "izinnya sudah diberi". Keduanya bisa
 * berbeda: di MIUI/HyperOS dengan "Mulai otomatis" mati, sistem menolak bind padahal grant-nya ada,
 * dan semua notifikasi hilang tanpa jejak. Tidak ada API publik untuk menanyakannya, jadi caranya
 * adalah post notifikasi sendiri lalu lihat apakah listener kita melihatnya.
 *
 * [lastSeenAt] hanya di memori: listener sekamar dengan UI, jadi kalau prosesnya mati, listener-nya
 * juga mati — dan itu memang harus dilaporkan merah, bukan dibaca dari cache disk.
 */
object ListenerProbe {

  @Volatile var lastSeenAt: Long = 0L

  fun markSeen(now: Long) {
    lastSeenAt = now
  }

  /** [BLOCKED_BY_OS] = probe-nya sendiri tidak sah (tidak bisa post), bukan listener mati. */
  enum class Result {
    OK,
    NOT_DELIVERED,
    BLOCKED_BY_OS,
  }

  suspend fun run(context: Context): Result {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return Result.BLOCKED_BY_OS

    val before = lastSeenAt

    // Varian compat, bukan android.app.NotificationChannel: kelas platform itu baru ada di API 26
    // sementara minSdk 24, jadi sekadar membuat objeknya melempar NoClassDefFoundError di Android 7.
    manager.createNotificationChannel(
      NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_MIN)
        .setName("Tes internal")
        .setShowBadge(false)
        .build()
    )
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("Tes pembaca notifikasi")
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .setAutoCancel(true)
        .setTimeoutAfter(TIMEOUT_MILLIS)
        .build()

    try {
      manager.notify(NOTIFICATION_ID, notification)
    } catch (e: SecurityException) {
      return Result.BLOCKED_BY_OS // izin dicabut di antara cek di atas dan baris ini
    }

    repeat((TIMEOUT_MILLIS / POLL_INTERVAL_MILLIS).toInt()) {
      if (lastSeenAt != before) return Result.OK
      delay(POLL_INTERVAL_MILLIS)
    }
    manager.cancel(NOTIFICATION_ID) // listener mati → tidak ada yang membatalkannya untuk kita
    return Result.NOT_DELIVERED
  }

  private const val CHANNEL_ID = "listener_selftest"
  private const val NOTIFICATION_ID = 4711
  private const val TIMEOUT_MILLIS = 3_000L
  private const val POLL_INTERVAL_MILLIS = 100L
}
