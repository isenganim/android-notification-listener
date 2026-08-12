package com.saquone.notificationlistener.data

import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Kontrak publik aplikasi ini — mengubahnya memecahkan integrasi orang lain. */
@Serializable
data class EventPayload(val package_name: String, val title: String, val text: String, val posted_at: Long)

/**
 * Notifikasi selalu masuk Room dulu ([enqueue], tanpa jaringan); pengiriman ([flush]) operasi
 * terpisah yang dijalankan WorkManager. Offline atau proses mati di tengah = tidak ada yang hilang.
 */
class Outbox(private val dao: EventDao, private val settings: Settings) {

  private val http = OkHttpClient()
  private val json = Json { encodeDefaults = true }

  suspend fun enqueue(pkg: String, title: String, text: String, postedAt: Long) {
    dao.insert(Event(pkg = pkg, title = title, text = text, postedAt = postedAt))
  }

  suspend fun flush() {
    val endpoint = settings.endpointNow()
    if (!endpoint.isConfigured) return
    for (e in dao.pending()) {
      try {
        val code = post(endpoint, e)
        if (code in 200..299) dao.markSent(e.id) else dao.markFailed(e.id, "HTTP $code")
      } catch (ex: IOException) {
        dao.markFailed(e.id, ex.message ?: "gagal terhubung")
      }
    }
    dao.purgeSent(System.currentTimeMillis() - PURGE_AFTER_MILLIS)
  }

  suspend fun sendTest(endpoint: Endpoint): Result<Int> = runCatching {
    post(
      endpoint,
      Event(
        pkg = "com.saquone.notificationlistener",
        title = "Tes koneksi",
        text = "Notifikasi contoh.",
        postedAt = System.currentTimeMillis(),
      ),
    )
  }

  private fun post(endpoint: Endpoint, e: Event): Int {
    val body =
      json.encodeToString(EventPayload(package_name = e.pkg, title = e.title, text = e.text, posted_at = e.postedAt))
    val request =
      Request.Builder()
        .url(endpoint.url)
        .post(body.toRequestBody(JSON_MEDIA_TYPE))
        .apply { if (endpoint.secret.isNotBlank()) header(SIGNATURE_HEADER, sign(endpoint.secret, body)) }
        .build()
    return http.newCall(request).execute().use { it.code }
  }

  companion object {
    /** Cocok dengan `webhook.Verify` di github.com/saquone/qris. */
    const val SIGNATURE_HEADER = "X-Signature"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private const val PURGE_AFTER_MILLIS = 7L * 24 * 3600 * 1000

    fun sign(secret: String, body: String): String {
      val mac = Mac.getInstance("HmacSHA256")
      mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
      return mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
    }
  }
}
