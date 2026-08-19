package com.saquone.notificationlistener.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

object DeviceHealthChecks {

  fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

  fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
  }

  /** ROM OEM kadang menghapus activity pengaturan yang dituju — jangan jatuhkan layar karenanya. */
  fun safeStartActivity(context: Context, intent: Intent) {
    try {
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "gagal membuka ${intent.action}", e)
    }
  }

  fun openNotificationListenerSettings(context: Context) =
    safeStartActivity(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

  fun openBatteryOptimizationRequest(context: Context) =
    safeStartActivity(
      context,
      Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
    )

  fun openAutostartSettings(context: Context, tip: OemGuidance.OemTip) =
    openFirstResolvable(context, tip.autostartComponents)

  /**
   * Buka activity OEM pertama yang benar-benar ada; semua gagal → detail aplikasi, yang selalu ada,
   * supaya tombolnya tidak pernah jadi tombol mati. Sebagian ROM menandai activity ini non-exported,
   * yang melempar [SecurityException] — bukan [ActivityNotFoundException].
   */
  fun openFirstResolvable(context: Context, componentSpecs: List<String>) {
    for (spec in componentSpecs) {
      val component = ComponentName.unflattenFromString(spec) ?: continue
      val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (context.packageManager.resolveActivity(intent, 0) == null) continue
      try {
        context.startActivity(intent)
        return
      } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "activity OEM $spec tidak bisa dibuka", e)
      } catch (e: SecurityException) {
        Log.w(TAG, "activity OEM $spec tidak diizinkan dibuka", e)
      }
    }
    safeStartActivity(
      context,
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
  }

  private const val TAG = "DeviceHealthChecks"
}
