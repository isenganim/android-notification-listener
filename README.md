# Android Notification Listener

Meneruskan notifikasi Android ke URL milikmu sendiri.

Pilih aplikasi mana yang boleh dibaca, isi satu URL, selesai. Tiap notifikasi yang masuk
dikirim sebagai JSON POST — ditandatangani HMAC-SHA256 kalau kamu mengisi secret.

**Tanpa akun. Tanpa login. Tanpa server perantara.** Datanya pergi ke tempat yang kamu
tentukan, dan tidak ke mana-mana lagi.

## Cara pakai

1. Pasang APK, buka, isi **URL endpoint** (+ secret kalau mau request ditandatangani)
2. Beri izin **akses notifikasi** saat diminta
3. Nyalakan switch untuk aplikasi yang mau dibaca
4. Tekan **Tes sekarang** untuk membuktikan listener benar-benar hidup

## Yang dikirim

```http
POST <URL kamu>
Content-Type: application/json
X-Signature: <HMAC-SHA256 hex dari body, kalau secret diisi>

{
  "package_name": "id.dana",
  "title": "Pembayaran Masuk",
  "text": "Rp50.137 diterima DANA Bisnis.",
  "posted_at": 1765432100000
}
```

`posted_at` = epoch milidetik (`StatusBarNotification.postTime`), bukan waktu kirim.

Balas **2xx** untuk menandai terkirim. Kode lain atau gagal koneksi membuat notifikasi
tetap di antrean dan dicoba lagi — **tidak ada yang hilang saat offline**. Notifikasi
selalu disimpan ke Room dulu, baru dikirim; kalau prosesnya dimatikan sistem di tengah
jalan, antreannya selamat.

Tidak ada parsing di aplikasi. Teks mentah dikirim apa adanya — server yang memutuskan
artinya. Pola teks notifikasi bank berubah sewaktu-waktu, dan mengubah aturan di server
jauh lebih murah daripada merilis ulang APK.

## Menerima tanpa menulis kode

[github.com/saquone/qris](https://github.com/Saquone/qris) (MIT) punya server siap pakai
yang menerima payload di atas apa adanya:

```bash
go install github.com/saquone/qris/cmd/qris-server@latest

cat > patterns.txt <<'EOF'
(?i)Rp\s?([0-9.,]+)\s*diterima
(?i)menerima Rp ?([0-9.,]+)
EOF

qris-server -secret whsec_rahasia -patterns patterns.txt
```

Isi URL `http://<ip-servermu>:8080/notification` dan secret yang sama di aplikasi, lalu tiap
notifikasi dijawab:

```json
{"amount":50137,"matched":true,"package_name":"id.dana","posted_at":1765432100000}
```

Pola dicoba berurutan, jadi baris lama tetap jadi fallback saat format teks bank berubah.

## Menerima di kodemu sendiri

Kalau mau logika sendiri, verifikasi tanda tangan dan ekstraksi nominal tersedia sebagai
library:

```go
import (
    "github.com/saquone/qris/notif"
    "github.com/saquone/qris/webhook"
)

parser, _ := notif.New([]string{`Rp\s?([0-9.,]+)\s*diterima`})

http.HandleFunc("/events", func(w http.ResponseWriter, r *http.Request) {
    body, _ := io.ReadAll(r.Body)
    if !webhook.Verify(secret, r.Header.Get("X-Signature"), body) {
        http.Error(w, "tanda tangan tidak cocok", http.StatusUnauthorized)
        return
    }

    var e struct {
        PackageName string `json:"package_name"`
        Title       string `json:"title"`
        Text        string `json:"text"`
        PostedAt    int64  `json:"posted_at"`
    }
    json.Unmarshal(body, &e)

    amount, err := parser.ParseAmount(e.Title + " " + e.Text)
    if err != nil {
        w.WriteHeader(http.StatusOK) // bukan notifikasi pembayaran, jangan minta kirim ulang
        return
    }
    // ... cocokkan `amount` dengan transaksimu
})
```

Server tidak harus Go — endpoint apa pun yang bisa menerima POST JSON sudah cukup.

## Kalau notifikasi tidak sampai

Izin diberikan ≠ layanan dijalankan. Di MIUI/HyperOS dengan "Mulai otomatis" mati, sistem
menolak mem-bind listener padahal izinnya ada — aplikasi tampak sehat sementara tidak ada
satu pun notifikasi terbaca.

Karena itu ada tombol **Tes sekarang**: aplikasi mem-post notifikasi ke dirinya sendiri
lalu memeriksa apakah listener-nya melihatnya. Kalau tidak sampai, layar utama menampilkan
langkah spesifik untuk merek HP-mu plus pintasan ke layar Autostart-nya.

## Build

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/
./gradlew assembleRelease   # tanpa keystore → APK unsigned
```

Butuh JDK 17 dan Android SDK (`local.properties` → `sdk.dir`). min SDK 24, target SDK 36.

Di-scaffold dengan `android create empty-activity`
([Android CLI](https://developer.android.com/tools)). Kotlin, Jetpack Compose Material 3
dengan warna dinamis di Android 12+, Navigation 3, Room, WorkManager, OkHttp. Tanpa
framework DI — tiga objek berumur panjang di satu container manual.

## Izin

| Izin | Kenapa |
|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | membaca notifikasi — inti aplikasi |
| `INTERNET` | mengirim ke endpoint-mu |
| `FOREGROUND_SERVICE`, `..._DATA_SYNC` | pengiriman antrean bertahan saat layar mati |
| `POST_NOTIFICATIONS` | notifikasi tes milik aplikasi sendiri |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | mencegah OEM mematikan listener |

WorkManager menambahkan `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, dan `RECEIVE_BOOT_COMPLETED`
lewat manifest merge — bukan dideklarasikan aplikasi ini, tapi tetap muncul di APK, jadi
disebut di sini supaya daftarnya jujur. Verifikasi sendiri:

```bash
aapt2 dump permissions app-debug.apk
```

Tidak ada `QUERY_ALL_PACKAGES`: daftar aplikasi diambil dari yang punya ikon peluncur saja.
Backup dimatikan supaya secret endpoint tidak ikut tersalin ke cloud lalu dipulihkan ke
perangkat lain.

## Lisensi

[GPL-3.0](LICENSE). Versi modifikasi wajib ikut membuka kode sumbernya — itu yang membuat
"aplikasi ini cuma meneruskan notifikasi ke alamatmu" bisa dibuktikan, bukan sekadar janji.
