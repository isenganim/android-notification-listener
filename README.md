# Saquone Notification Listener

[![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Self-Host Ready](https://img.shields.io/badge/Self--Host-Ready-orange.svg)](#)
[![Android Min SDK](https://img.shields.io/badge/Min_SDK-24-green.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-36-brightgreen.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_M3-purple.svg)](https://developer.android.com/jetpack/compose)

Listener notifikasi Android **Self-Hosted** open-source untuk **QRIS**: membaca notifikasi pembayaran dari aplikasi merchant (seperti **DANA Bisnis**), meneruskannya ke server milikmu sendiri (*self-hosted*) secara aman via webhook, dan memverifikasi nominal transaksi secara otomatis.

Pilih aplikasi pembayaran yang ingin dibaca, atur **URL Endpoint**, selesai. Setiap notifikasi diteruskan sebagai HTTP JSON POST bertanda tangan **HMAC-SHA256**.

🔒 **100% Self-Hosted. Tanpa akun. Tanpa login. Tanpa server perantara pihak ketiga. 100% Privasi Terjamin.**

---

## 🌟 Fitur Utama

- **🎨 Material Design 3 & Material You (Dynamic Colors):** Tampilan visual modern yang menyesuaikan warna wallpaper HP secara otomatis di Android 12+ (HyperOS, OneUI, Pixel UI).
- **🎬 Material Motion Navigation:** Transisi perpindahan layar yang mulus dengan standar *Material 3 Shared Axis (X)* dan dukungan *Predictive Back Gesture*.
- **🔄 Pull-To-Refresh (Usap LAYAR ke Bawah):**
  - **Di Halaman Aplikasi:** Mengusap ke bawah langsung mengambil katalog gateway terbaru dari `qris-server` (`GET /gateways`).
  - **Di Halaman Log:** Mengusap ke bawah memicu *Flush & Sync Penuh* untuk mengirimkan antrean notifikasi pending dan memperbarui status verifikasi dari server secara *real-time*.
- **📜 Infinite Scroll dengan Paginasi:** Riwayat notifikasi di halaman Log dimuat secara bertahap (*batch 20 item per load*) sehingga tetap cepat dan efisien.
- **🛠️ Tombol "Update Parser & Simpan ke Room":** Mengunduh aturan regex parser dari `qris-server` dan menyimpannya secara permanen ke database lokal **Room DB** HP untuk dukungan offline.
- **🛡️ Strictly Active Catalog & Auto-Pruning:** Pembaca notifikasi memfilter secara ketat aplikasi yang aktif di katalog server. Aplikasi di luar katalog (seperti notifikasi promo atau driver) diabaikan secara otomatis.
- **⚡ Reliable Outbox Pattern (Anti Loss):** Notifikasi selalu disimpan ke Room DB lokal terlebih dahulu sebelum dikirim. Jika sinyal mati atau aplikasi dimatikan OS, notifikasi tersimpan aman dan dikirim otomatis saat online kembali.
- **🩺 Self-Diagnostic Tool ("Tes Sekarang"):** Fitur `ListenerProbe` untuk membuktikan apakah listener aktif dan ter-bind oleh sistem Android, lengkap dengan bantuan pintasan *Autostart*, *Optimasi Baterai*, dan panduan vendor HP (*Don'tKillMyApp*).

---

## 🚀 Cara Penggunaan

1. **Pasang & Buka APK** di HP Android.
2. Isi **URL Endpoint** (contoh: `http://192.168.1.10:8080/notification`) dan *Secret Key* HMAC (opsional).
3. Berikan izin **Akses Notifikasi** saat diminta.
4. Di tab **Aplikasi**, aktifkan switch untuk aplikasi pembayaran yang ingin dibaca (contoh: **DANA Bisnis**).
5. Di tab **Status**, tekan **"Tes sekarang"** untuk memverifikasi layanan aktif.

---

## ⚙️ Aplikasi Gateway & Katalog

Aplikasi ini bersifat **Config-Driven**: daftar aplikasi pembayaran yang didukung **tidak di-hardcode di APK**, melainkan datang dari server (`GET /gateways`) dan di-cache ke **Room DB**.

Menambah dukungan bank/e-wallet baru cukup dilakukan di sisi server tanpa perlu rilis ulang APK:

```bash
qris-server -catalog gateways-custom.json
```

Lalu tekan tombol **"Update Parser & Simpan ke Room"** atau usap layar ke bawah (*Pull-To-Refresh*) di tab Aplikasi.

---

## 📩 Format HTTP Webhook Payload

Setiap notifikasi yang tertangkap dikirim via HTTP POST:

```http
POST /notification HTTP/1.1
Host: server-kamu.com
Content-Type: application/json
X-Signature: c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2

{
  "package_name": "id.dana",
  "title": "Pembayaran Masuk",
  "text": "Rp1.426 diterima DANA Bisnis.",
  "posted_at": 1787117948509
}
```

* **Header `X-Signature`:** Hasil HMAC-SHA256 hex digest dari raw JSON body menggunakan *Secret Key* yang kamu atur.
* **`posted_at`:** Epoch timestamp milidetik (`StatusBarNotification.postTime`) saat notifikasi muncul di HP.
* **Respon Server:** Balas dengan HTTP Status Code `2xx` (misal `200 OK`) untuk mengonfirmasi sukses. Jika server membalas selain `2xx` atau koneksi terputus, notifikasi akan tersimpan di antrean Room dan dicoba ulang secara otomatis.

---

## 💻 Server Pasangan Resmi

Aplikasi ini merupakan pasangan resmi dari modul library open-source **[github.com/saquone/qris](https://github.com/Saquone/qris)** (Go, MIT License):

```bash
# 1. Jalankan qris-server
go install github.com/saquone/qris/cmd/qris-server@latest
qris-server -secret secret_rahasia_kamu

# 2. Buat tagihan QRIS dinamis (misal Rp1.000 + kode unik 426 = Rp1.426)
curl -X POST http://localhost:8080/charges -d '{"amount":1000}'
# → {"id":3,"amount":1426,"payload":"0002010102122665...","status":"pending"}

# 3. Setelah pembeli membayar Rp1.426 via QRIS, listener akan meneruskan notifikasi
#    dan server otomatis mengubah status tagihan menjadi lunas ("paid")!
curl http://localhost:8080/charges/3
# → {"id":3,"status":"paid","paid_at":1787117996120}
```

---

## 🛠️ Kompilasi & Build

```bash
# Build APK Debug
./gradlew assembleDebug

# Build APK Release (membutuhkan secrets/keystore.properties jika signed)
./gradlew assembleProdRelease

# Jalankan Unit Tests
./gradlew test
```

* **Persyaratan Build:** JDK 17, Android SDK (`minSdk 24`, `targetSdk 36`).
* **Teknologi:** Kotlin, Jetpack Compose Material 3, Navigation 3 (`androidx.navigation3`), Room DB, WorkManager, OkHttp3, Kotlin Serialization.

---

## 🔑 Hak Akses / Izin (Permissions)

| Izin Android | Kegunaan & Alasan |
|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Membaca notifikasi dari aplikasi merchant |
| `INTERNET` | Mengirim data notifikasi ke endpoint server kamu |
| `FOREGROUND_SERVICE` & `DATA_SYNC` | Memastikan pengiriman outbox bertahan saat layar mati |
| `POST_NOTIFICATIONS` | Mengirim notifikasi tes mandiri (`ListenerProbe`) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Mencegah sistem mematikan listener di latar belakang |

* `QUERY_ALL_PACKAGES` **tidak digunakan**. Sebagai gantinya digunakan query intent `LAUNCHER` yang aman dan sesuai kebijakan Google Play Store / Android 11+.

---

## 📄 Lisensi

Proyek ini dirilis di bawah lisensi **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)**.
Setiap modifikasi atau pendistribusian ulang wajib menyertakan kode sumber lengkap.
