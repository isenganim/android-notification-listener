package com.saquone.notificationlistener.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Endpoint(val url: String, val secret: String) {
  val isConfigured: Boolean
    get() = url.isNotBlank()
}

class Settings(private val context: Context) {

  val endpoint: Flow<Endpoint> = context.dataStore.data.map { Endpoint(it[URL].orEmpty(), it[SECRET].orEmpty()) }

  /** Package yang notifikasinya diteruskan. Kosong = tidak ada yang dibaca. */
  val watched: Flow<Set<String>> = context.dataStore.data.map { it[WATCHED] ?: emptySet() }

  suspend fun endpointNow(): Endpoint = endpoint.first()

  suspend fun watchedNow(): Set<String> = watched.first()

  suspend fun saveEndpoint(url: String, secret: String) {
    context.dataStore.edit {
      it[URL] = url.trim()
      it[SECRET] = secret.trim()
    }
  }

  suspend fun setWatched(pkg: String, on: Boolean) {
    context.dataStore.edit {
      val current = it[WATCHED] ?: emptySet()
      it[WATCHED] = if (on) current + pkg else current - pkg
    }
  }

  private companion object {
    val URL = stringPreferencesKey("endpoint_url")
    val SECRET = stringPreferencesKey("endpoint_secret")
    val WATCHED = stringSetPreferencesKey("watched_packages")
  }
}
