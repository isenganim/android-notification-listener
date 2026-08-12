package com.saquone.notificationlistener.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saquone.notificationlistener.container
import com.saquone.notificationlistener.data.Endpoint
import com.saquone.notificationlistener.data.Event
import com.saquone.notificationlistener.data.Gateway
import com.saquone.notificationlistener.util.DeviceHealthChecks
import com.saquone.notificationlistener.util.ListenerProbe
import com.saquone.notificationlistener.util.OemGuidance
import com.saquone.notificationlistener.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Satu aplikasi gateway: sudah terpasang atau belum, dan sedang dibaca atau tidak. */
data class GatewayApp(
  val packageName: String,
  val label: String,
  val gatewayLabel: String,
  val installed: Boolean,
  val watched: Boolean,
)

data class UiState(
  val endpoint: Endpoint = Endpoint("", ""),
  val endpointLoaded: Boolean = false,
  val listenerEnabled: Boolean = false,
  val batteryExempt: Boolean = false,
  val probe: ListenerProbe.Result? = null,
  val probeRunning: Boolean = false,
  val apps: List<GatewayApp> = emptyList(),
  val appsLoading: Boolean = true,
  val catalogSize: Int = 0,
  val catalogSyncing: Boolean = false,
  val events: List<Event> = emptyList(),
  val pendingCount: Int = 0,
  val message: String? = null,
) {
  val watchedCount: Int
    get() = apps.count { it.watched }

  val installedCount: Int
    get() = apps.count { it.installed }
}

class ListenerViewModel(app: Application) : AndroidViewModel(app) {

  private val container = app.container
  private val _state = MutableStateFlow(UiState())
  val state: StateFlow<UiState> = _state.asStateFlow()

  val oemTip: OemGuidance.OemTip = OemGuidance.forManufacturer()

  init {
    viewModelScope.launch {
      container.settings.endpoint.collect { e -> _state.update { it.copy(endpoint = e, endpointLoaded = true) } }
    }
    viewModelScope.launch { container.events.recent().collect { l -> _state.update { it.copy(events = l) } } }
    viewModelScope.launch { container.events.pendingCount().collect { c -> _state.update { it.copy(pendingCount = c) } } }
    viewModelScope.launch { container.settings.watched.collect { refreshApps(it) } }
    viewModelScope.launch { syncCatalog(silent = true) }
    WorkScheduler.schedulePeriodic(app)
  }

  fun refreshPermissions() {
    val context = getApplication<Application>()
    _state.update {
      it.copy(
        listenerEnabled = DeviceHealthChecks.isNotificationListenerEnabled(context),
        batteryExempt = DeviceHealthChecks.isIgnoringBatteryOptimizations(context),
      )
    }
  }

  fun saveEndpoint(url: String, secret: String) {
    viewModelScope.launch {
      container.settings.saveEndpoint(url, secret)
      syncCatalog(silent = true)
    }
  }

  fun sendTest(url: String, secret: String) {
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) { container.outbox.sendTest(Endpoint(url.trim(), secret.trim())) }
      val message =
        result.fold(
          onSuccess = { code ->
            if (code in 200..299) "Berhasil — server menjawab HTTP $code."
            else "Server menjawab HTTP $code. Endpoint tercapai, tapi menolak."
          },
          onFailure = { "Gagal terhubung: ${it.message ?: "periksa URL dan koneksi"}" },
        )
      _state.update { it.copy(message = message) }
    }
  }

  /** Ambil katalog terbaru dari server; gagal = tetap pakai yang tersimpan. */
  fun syncCatalog(silent: Boolean = false) {
    viewModelScope.launch {
      if (!silent) _state.update { it.copy(catalogSyncing = true) }
      val result = withContext(Dispatchers.IO) { container.catalog.sync() }
      _state.update { it.copy(catalogSyncing = false) }
      refreshApps(container.settings.watchedNow())
      if (!silent) {
        _state.update {
          it.copy(
            message =
              result.fold(
                onSuccess = { n -> "Katalog diperbarui — $n gateway." },
                onFailure = { e -> "Gagal ambil katalog: ${e.message}. Memakai salinan tersimpan." },
              )
          )
        }
      }
    }
  }

  fun setWatched(pkg: String, on: Boolean) {
    viewModelScope.launch { container.settings.setWatched(pkg, on) }
  }

  fun runProbe() {
    viewModelScope.launch {
      _state.update { it.copy(probeRunning = true) }
      val result = ListenerProbe.run(getApplication())
      _state.update { it.copy(probe = result, probeRunning = false) }
    }
  }

  fun flushNow() {
    WorkScheduler.flushNow(getApplication())
    _state.update { it.copy(message = "Pengiriman dijadwalkan.") }
  }

  fun clearLog() {
    viewModelScope.launch { container.events.clear() }
  }

  fun messageShown() = _state.update { it.copy(message = null) }

  fun openListenerSettings() = DeviceHealthChecks.openNotificationListenerSettings(getApplication())

  fun openBatterySettings() = DeviceHealthChecks.openBatteryOptimizationRequest(getApplication())

  fun openAutostartSettings() = DeviceHealthChecks.openAutostartSettings(getApplication(), oemTip)

  fun openOemGuide() = DeviceHealthChecks.openDontKillMyAppGuide(getApplication(), oemTip)

  /**
   * Yang ditampilkan HANYA aplikasi yang ada di katalog — bukan seluruh isi HP. Daftarnya datang
   * dari qris-server, jadi menambah dukungan gateway baru tidak butuh rilis ulang APK.
   */
  private suspend fun refreshApps(watched: Set<String>) {
    val pm = getApplication<Application>().packageManager
    val gateways: List<Gateway> = container.catalog.current()
    val apps =
      withContext(Dispatchers.IO) {
        gateways
          .flatMap { g -> g.packages.map { pkg -> g to pkg } }
          .map { (g, pkg) ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            GatewayApp(
              packageName = pkg,
              label = label ?: g.label,
              gatewayLabel = g.label,
              installed = label != null,
              watched = pkg in watched,
            )
          }
          .sortedWith(
            compareByDescending<GatewayApp> { it.watched }
              .thenByDescending { it.installed }
              .thenBy { it.gatewayLabel.lowercase() }
          )
      }
    _state.update { it.copy(apps = apps, appsLoading = false, catalogSize = gateways.size) }
  }
}
