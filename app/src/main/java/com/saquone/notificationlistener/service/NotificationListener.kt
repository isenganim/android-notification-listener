package com.saquone.notificationlistener.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.saquone.notificationlistener.container
import com.saquone.notificationlistener.data.parseAmount
import com.saquone.notificationlistener.util.ListenerProbe
import com.saquone.notificationlistener.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Menyimpan notifikasi ke Room SEBELUM apa pun menyentuh jaringan. Offline atau proses mati tepat
 * setelahnya = tidak ada yang hilang. Tidak ada parsing di sini; teks mentah diteruskan apa adanya.
 */
class NotificationListener : NotificationListenerService() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    if (handleProbe(sbn)) return
    scope.launch { ingest(sbn) }
  }

  /**
   * Sapu notifikasi yang sudah ada di shade saat binding. Tanpa ini, notifikasi yang datang selagi
   * listener tidak ter-bind (proses dimatikan OEM, rebind setelah update OS) hilang permanen —
   * [onNotificationPosted] hanya dipanggil untuk notifikasi baru. Duplikat ditangani unique index.
   */
  override fun onListenerConnected() {
    scope.launch {
      val active = runCatching { activeNotifications }.getOrNull() ?: return@launch
      for (sbn in active) {
        if (handleProbe(sbn)) continue
        ingest(sbn)
      }
    }
  }

  override fun onListenerDisconnected() {
    requestRebind(ComponentName(this, NotificationListener::class.java))
  }

  /** Notifikasi milik sendiri = probe [ListenerProbe]. Ditandai, dibatalkan, tidak diteruskan. */
  private fun handleProbe(sbn: StatusBarNotification): Boolean {
    if (sbn.packageName != packageName) return false
    ListenerProbe.markSeen(System.currentTimeMillis())
    runCatching { cancelNotification(sbn.key) }
    return true
  }

  private suspend fun ingest(sbn: StatusBarNotification) {
    if (sbn.packageName !in container.settings.watchedNow()) return
    val gateway = container.catalog.current().firstOrNull { sbn.packageName in it.packages }
    val extras = sbn.notification.extras
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
    val text =
      listOfNotNull(extras.getCharSequence(Notification.EXTRA_TEXT), extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        .joinToString(" ")
        .trim()
    if (title.isBlank() && text.isBlank()) return

    // Nominal dibaca lokal hanya untuk ditampilkan di log; server tetap menghitung ulang sendiri.
    val amount = gateway?.let { parseAmount(it.patterns, "$title $text") }
    container.outbox.enqueue(sbn.packageName, title, text, sbn.postTime, amount)
    WorkScheduler.flushNow(applicationContext)
  }
}
