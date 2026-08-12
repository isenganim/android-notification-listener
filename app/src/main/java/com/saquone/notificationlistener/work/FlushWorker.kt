package com.saquone.notificationlistener.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saquone.notificationlistener.container
import java.util.concurrent.TimeUnit

class FlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    applicationContext.container.outbox.flush()
    return Result.success()
  }
}

object WorkScheduler {

  /**
   * `KEEP`: flush yang sudah antre akan ikut mengambil baris baru (membaca 50 sekaligus).
   * `RUN_AS_NON_EXPEDITED_WORK_REQUEST` supaya kerjaannya tetap jalan saat kuota expedited habis —
   * `DROP_WORK_REQUEST` akan membuangnya diam-diam.
   */
  fun flushNow(context: Context) {
    val request =
      OneTimeWorkRequestBuilder<FlushWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setConstraints(NETWORK_REQUIRED)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_FLUSH, ExistingWorkPolicy.KEEP, request)
  }

  /** 15 menit = minimum WorkManager untuk kerja periodik. */
  fun schedulePeriodic(context: Context) {
    val request =
      PeriodicWorkRequestBuilder<FlushWorker>(15, TimeUnit.MINUTES).setConstraints(NETWORK_REQUIRED).build()
    WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
  }

  private val NETWORK_REQUIRED = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
  private const val UNIQUE_FLUSH = "flush_outbox"
  private const val UNIQUE_PERIODIC = "flush_outbox_periodic"
}
