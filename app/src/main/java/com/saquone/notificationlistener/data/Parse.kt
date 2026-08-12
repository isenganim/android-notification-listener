package com.saquone.notificationlistener.data

/**
 * Menyalin persis semantik `notif.ParseAmount` di github.com/saquone/qris: pola dicoba berurutan,
 * grup 1 dibuang pemisah `.`/`,`/spasi, nominal <= 0 ditolak. Kalau menyimpang, nominal di layar
 * akan bertentangan dengan yang dihitung server.
 *
 * Pola malformed dilewati — katalog datang dari jaringan dan listener tidak boleh crash karenanya.
 * Catatan: Go memakai RE2, Kotlin memakai regex Java, jadi pola dengan lookahead bisa cocok di
 * sini tapi ditolak server.
 */
fun parseAmount(patterns: List<String>, text: String): Long? {
  for (pattern in patterns) {
    val amount =
      runCatching {
          Regex(pattern).find(text)?.groupValues?.getOrNull(1)?.filterNot { it == '.' || it == ',' || it == ' ' }?.toLongOrNull()
        }
        .getOrNull()
    if (amount != null && amount > 0) return amount
  }
  return null
}
