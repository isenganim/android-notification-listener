# Saquone Notification Listener

Listener notifikasi Android untuk **QRIS**: baca notifikasi aplikasi merchant, teruskan ke
server milikmu, dan biarkan nominalnya diverifikasi otomatis terhadap tagihan yang menunggu.

Pilih aplikasi merchant mana yang boleh dibaca, isi satu URL, selesai. Tiap notifikasi yang
masuk dikirim sebagai JSON POST — ditandatangani HMAC-SHA256 kalau kamu mengisi secret.

**Tanpa akun. Tanpa login. Tanpa server perantara.** Datanya pergi ke tempat yang kamu
tentukan, dan tidak ke mana-mana lagi.

## Cara pakai

1. Pasang APK, buka, isi **URL endpoint** (+ secret kalau mau request ditandatangani)
2. Beri izin **akses notifikasi** saat diminta
3. Buka tab **Aplikasi**, nyalakan switch untuk aplikasi pembayaran yang mau dibaca
4. Tab **Status** → **Tes sekarang** untuk membuktikan listener benar-benar hidup

Tiga tab: **Status** (kesehatan + endpoint), **Aplikasi** (pilih yang dibaca), **Log**.

Tab **Log** menampilkan, per notifikasi: ikon aplikasi sumbernya, apakah **nominal terbaca**,
dan apakah pembayarannya **terverifikasi** — yaitu nominalnya cocok persis dengan tagihan yang
menunggu di server dan tagihan itu ditandai lunas. Dua hal yang berbeda dan sering tertukar.

### Aplikasi mana yang muncul

Hanya aplikasi pembayaran yang didukung — DANA Bisnis, GoPay Merchant, Grab Merchant,
BRI Merchant, ShopeePay Merchant, OVO, dan lainnya. Bukan seluruh isi HP.

Daftarnya **tidak ditulis di aplikasi ini**. Sumbernya `GET /gateways` di
[qris-server](https://github.com/Saquone/qris), diambil saat online dan disimpan ke Room
sebagai **cache** supaya tetap jalan offline — sumber kebenarannya tetap server.

Gateway yang polanya belum dicocokkan dengan teks notifikasi asli ditandai
"pola belum diverifikasi", bukan disamarkan seolah sudah pasti jalan. Menambah dukungan gateway baru cukup di server:

```bash
qris-server -catalog gateways-saya.json
```

lalu tekan ikon refresh di tab Aplikasi — **tanpa membangun ulang APK**. Salinan bawaan
ada di `app/src/main/assets/gateways.json` supaya aplikasi tetap berguna sebelum pernah
tersambung.

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

Teks mentah dikirim apa adanya — **server yang berwenang** memutuskan artinya. Aplikasi juga
membaca nominalnya sendiri memakai pola dari katalog, tapi itu hanya untuk ditampilkan di tab
Log; hasilnya tidak ikut dikirim.

## Server pasangannya

[github.com/saquone/qris](https://github.com/Saquone/qris) (MIT) punya server siap pakai —
**tanpa satu baris kode pun yang perlu kamu tulis**:

```bash
go install github.com/saquone/qris/cmd/qris-server@latest
qris-server -secret whsec_rahasia
```

Isi URL `http://<ip-servermu>:8080/notification` + secret yang sama di aplikasi. Alur penuhnya:

```bash
# 1. unggah QRIS statis merchant (gambarnya disimpan di folder -qris-dir)
curl -X POST localhost:8080/qris --data-binary @qris-toko.png

# 2. buat tagihan — kode unik ditambahkan supaya nominalnya tidak pernah kembar
curl -X POST localhost:8080/charges -d '{"amount":50000}'
# → {"id":1,"amount":50684,"payload":"0002010102122665...","status":"pending"}

# 3. pembeli bayar Rp50.684 → notifikasi masuk → aplikasi ini meneruskannya
#    → server mencocokkan dan menandai tagihan lunas
curl localhost:8080/charges/1
# → {"status":"paid","paid_at":...}
```

Jawaban tiap notifikasi memuat status verifikasinya, dan itulah yang ditampilkan di tab Log:

```json
{"amount":50684,"matched":true,"verified":true,"charge_id":1}
```

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

Karena itu ada tombol **Tes sekarang** di tab Status: aplikasi mem-post notifikasi ke dirinya
sendiri lalu memeriksa apakah listener-nya melihatnya. Kalau tidak sampai, muncul langkah
spesifik untuk merek HP-mu plus pintasan ke layar Autostart-nya.

Diuji di Xiaomi (HyperOS): izin sudah diberikan dan aplikasi tampak aktif, tapi sistem menolak
mem-bind listener-nya — persis kasus yang tombol ini cari.

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

Tidak ada `QUERY_ALL_PACKAGES`. Sebagai gantinya `<queries>` dengan filter intent LAUNCHER —
sejak Android 11 aplikasi tidak bisa melihat paket lain tanpa itu, dan tanpanya semua gateway
tampak "belum terpasang". Yang terlihat hanya aplikasi berikon peluncur, bukan seluruh isi HP.
Backup dimatikan supaya secret endpoint tidak ikut tersalin ke cloud lalu dipulihkan ke
perangkat lain.

## Lisensi

[GPL-3.0](LICENSE). Versi modifikasi wajib ikut membuka kode sumbernya — itu yang membuat
"aplikasi ini cuma meneruskan notifikasi ke alamatmu" bisa dibuktikan, bukan sekadar janji.
