package com.saquone.notificationlistener.util

import android.os.Build

/**
 * Panduan per-pabrikan. Beberapa OEM menjalankan pembunuh aplikasi latar belakang sendiri di atas
 * Doze bawaan. Tidak ada API publik untuk mendeteksi atau memperbaikinya — hanya pencocokan nama
 * pabrikan + tautan ke dontkillmyapp.com.
 */
object OemGuidance {

  /**
   * [autostartComponents] = kandidat activity "Autostart" milik OEM (`pkg/kelas`). Bukan API
   * publik, bisa hilang antar versi ROM — karena itu berbentuk daftar dan dipakai lewat
   * [DeviceHealthChecks.openFirstResolvable] yang jatuh ke detail aplikasi kalau semuanya gagal.
   */
  data class OemTip(
    val vendorLabel: String,
    val message: String,
    val dontKillMyAppUrl: String,
    val autostartComponents: List<String> = emptyList(),
  ) {
    val probeNotDeliveredMessage: String
      get() =
        "Notifikasi tes tidak sampai. Izin sudah diberi, tapi sistem belum menghidupkan layanannya — " +
          "di $vendorLabel biasanya karena \"Mulai otomatis\"/Autostart masih mati."

    val probeBlockedMessage: String
      get() = "Notifikasi aplikasi ini sedang dimatikan, jadi tes tidak bisa dijalankan. Izinkan dulu."
  }

  fun forManufacturer(manufacturer: String = Build.MANUFACTURER): OemTip {
    val slug = manufacturer.trim().lowercase().replace(" ", "")

    val (label, message) =
      when {
        slug.contains("huawei") ->
          "Huawei" to
            "Huawei paling agresif mematikan aplikasi latar belakang. Pengaturan baterai → App launch → " +
              "cari aplikasi ini → matikan \"Manage automatically\", lalu aktifkan Auto-launch, Secondary " +
              "launch, dan Run in background."
        slug.contains("xiaomi") || slug.contains("redmi") || slug.contains("poco") ->
          "Xiaomi (MIUI)" to
            "MIUI sangat agresif menutup aplikasi latar belakang. Pengaturan → Aplikasi → Kelola aplikasi → " +
              "aplikasi ini → Hemat baterai \"Tanpa batas\", lalu aktifkan \"Autostart\"."
        slug.contains("oneplus") ->
          "OnePlus" to
            "Pengaturan baterai → aplikasi ini → matikan optimasi baterai, pastikan izin latar belakang aktif."
        slug.contains("samsung") ->
          "Samsung" to
            "Pengaturan → Perawatan baterai & perangkat → Baterai → Batasan latar belakang, pastikan " +
              "aplikasi ini tidak masuk daftar \"Tidur mendalam\"."
        slug.contains("oppo") ->
          "Oppo" to "Pengaturan baterai → aplikasi ini, izinkan \"Autostart\" dan matikan optimasi baterai."
        slug.contains("vivo") ->
          "Vivo" to
            "Pengaturan baterai → aplikasi ini, izinkan \"Autostart tinggi\" dan matikan optimasi baterai."
        slug.contains("realme") ->
          "Realme" to "Pengaturan baterai → aplikasi ini, izinkan \"Autostart\" dan matikan optimasi baterai."
        else ->
          manufacturer.trim().ifBlank { "Perangkat ini" } to
            "Sebagian pabrikan membatasi aplikasi latar belakang demi hemat baterai. Kecualikan aplikasi ini " +
              "dari optimasi baterai, dan aktifkan \"Autostart\" kalau tersedia."
      }

    return OemTip(label, message, "https://dontkillmyapp.com/$slug", autostartComponentsFor(slug))
  }

  private fun autostartComponentsFor(slug: String): List<String> =
    when {
      slug.contains("xiaomi") || slug.contains("redmi") || slug.contains("poco") ->
        listOf("com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity")
      slug.contains("huawei") ->
        listOf(
          "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
          "com.huawei.systemmanager/com.huawei.systemmanager.optimize.process.ProtectActivity",
        )
      slug.contains("oppo") || slug.contains("realme") ->
        listOf(
          "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
          "com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity",
          "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity",
        )
      slug.contains("vivo") ->
        listOf(
          "com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
          "com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        )
      slug.contains("oneplus") ->
        listOf("com.oneplus.security/com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
      else -> emptyList()
    }
}
