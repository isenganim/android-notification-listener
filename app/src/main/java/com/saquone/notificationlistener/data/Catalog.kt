package com.saquone.notificationlistener.data

import android.content.Context
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Bentuknya sama persis dengan `catalog/gateways.json` di github.com/saquone/qris. */
@Serializable
data class GatewayDto(
  val key: String,
  val label: String,
  val packages: List<String>,
  val patterns: List<String>,
  val verified: Boolean = false,
)

/**
 * Katalog gateway. Daftar aplikasi yang didukung TIDAK ditulis di aplikasi ini — sumbernya
 * `GET <origin>/gateways` di qris-server, disimpan ke Room sebagai **cache** supaya tetap jalan
 * offline. Salinan bawaan di assets dipakai sebelum pernah tersambung.
 */
class Catalog(
  private val context: Context,
  private val settings: Settings,
  private val dao: GatewayDao,
  private val eventDao: EventDao,
) {

  private val http = OkHttpClient()
  private val json = Json { ignoreUnknownKeys = true }

  /** Katalog tersimpan; kalau Room masih kosong, seed dari assets dulu. */
  suspend fun current(): List<GatewayEntity> {
    if (dao.count() == 0) save(bundled())
    return dao.all()
  }

  fun flow() = dao.allFlow()

  private fun bundled(): List<GatewayDto> =
    context.assets.open("gateways.json").use { json.decodeFromString(it.readBytes().decodeToString()) }

  private suspend fun save(list: List<GatewayDto>) {
    val now = System.currentTimeMillis()
    dao.upsert(list.map { GatewayEntity(it.key, it.label, it.packages, it.patterns, it.verified, now) })
    dao.deleteMissing(list.map { it.key })
    val validPackages = list.flatMap { it.packages }
    settings.pruneWatched(validPackages.toSet())
    eventDao.purgeUnmatchedPackages(validPackages)
  }

  /**
   * Ambil katalog terbaru dari server. Gagal = tetap pakai yang tersimpan; katalog basi jauh
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
      val list = json.decodeFromString<List<GatewayDto>>(body)
      if (list.isEmpty()) throw IOException("katalog kosong")
      save(list)
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
