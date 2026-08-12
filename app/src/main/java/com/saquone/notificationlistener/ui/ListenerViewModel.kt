package com.saquone.notificationlistener.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saquone.notificationlistener.container
import com.saquone.notificationlistener.data.Endpoint
import com.saquone.notificationlistener.data.Event
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

data class InstalledApp(val packageName: String, val label: String, val watched: Boolean)

data class UiState(
  val endpoint: Endpoint = Endpoint("", ""),
  val endpointLoaded: Boolean = false,
  val listenerEnabled: Boolean = false,
  val batteryExempt: Boolean = false,
  val probe: ListenerProbe.Result? = null,
  val probeRunning: Boolean = false,
  val apps: List<InstalledApp> = emptyList(),
  val appsLoading: Boolean = true,
  val events: List<Event> = emptyList(),
  val pendingCount: Int = 0,
  val message: String? = null,
) {
  val watchedCount: Int
    get() = apps.count { it.watched }
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
    viewModelScope.launch { container.settings.saveEndpoint(url, secret) }
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

  /** Hanya aplikasi berikon peluncur — cukup untuk memilih bank/e-wallet tanpa QUERY_ALL_PACKAGES. */
  private suspend fun refreshApps(watched: Set<String>) {
    val pm = getApplication<Application>().packageManager
    val apps =
      withContext(Dispatchers.IO) {
        pm.getInstalledApplications(0)
          .asSequence()
          .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName in watched }
          .filter { pm.getLaunchIntentForPackage(it.packageName) != null || it.packageName in watched }
          .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString(), it.packageName in watched) }
          .sortedWith(compareByDescending<InstalledApp> { it.watched }.thenBy { it.label.lowercase() })
          .toList()
      }
    _state.update { it.copy(apps = apps, appsLoading = false) }
  }
}
