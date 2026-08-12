package com.saquone.notificationlistener.data

import android.content.Context
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Satu gateway pembayaran: aplikasi mana yang notifikasinya dibaca, dan pola apa yang dipakai
 * membaca nominalnya. Bentuknya sama persis dengan `catalog/gateways.json` di
 * github.com/saquone/qris.
 */
@Serializable
data class Gateway(val key: String, val label: String, val packages: List<String>, val patterns: List<String>)

/**
 * Katalog gateway. Daftar aplikasi yang didukung TIDAK ditulis di aplikasi ini — sumbernya
 * `GET <origin>/gateways` di qris-server. Salinan bawaan di assets dipakai saat pertama jalan
 * atau ketika server tidak bisa dihubungi, jadi aplikasi tetap berguna offline.
 */
class Catalog(private val context: Context, private val settings: Settings) {

  private val http = OkHttpClient()
  private val json = Json { ignoreUnknownKeys = true }

  @Volatile private var cached: List<Gateway>? = null

  fun bundled(): List<Gateway> =
    context.assets.open("gateways.json").use { json.decodeFromString(it.readBytes().decodeToString()) }

  /** Katalog tersimpan, atau bawaan bila belum pernah sinkron. */
  suspend fun current(): List<Gateway> {
    cached?.let {
      return it
    }
    val stored = settings.catalogJsonNow()
    val list = if (stored.isBlank()) bundled() else runCatching { json.decodeFromString<List<Gateway>>(stored) }.getOrElse { bundled() }
    cached = list
    return list
  }

  /**
   * Ambil katalog terbaru dari server. Gagal = diam-diam pakai yang tersimpan; katalog basi jauh
   * lebih baik daripada aplikasi yang tidak bisa dipakai saat server mati.
   */
  suspend fun sync(): Result<Int> {
    val endpoint = settings.endpointNow()
    if (!endpoint.isConfigured) return Result.failure(IOException("endpoint belum diatur"))
    val url = endpoint.url.toGatewaysUrl() ?: return Result.failure(IOException("URL endpoint tidak valid"))
    return runCatching {
      val body =
        http.newCall(Request.Builder().url(url).build()).execute().use {
          if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
          it.body.string()
        }
      val list = json.decodeFromString<List<Gateway>>(body)
      if (list.isEmpty()) throw IOException("katalog kosong")
      settings.saveCatalogJson(body)
      cached = list
      list.size
    }
  }
}

/** `http://host:8080/notification` → `http://host:8080/gateways`. */
internal fun String.toGatewaysUrl(): String? {
  val trimmed = trimEnd('/')
  val schemeEnd = trimmed.indexOf("://").takeIf { it > 0 } ?: return null
  val pathStart = trimmed.indexOf('/', schemeEnd + 3)
  val origin = if (pathStart == -1) trimmed else trimmed.substring(0, pathStart)
  return "$origin/gateways"
}
