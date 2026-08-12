package com.saquone.notificationlistener.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saquone.notificationlistener.util.ListenerProbe

@Composable
fun StatusScreen(
  state: UiState,
  viewModel: ListenerViewModel,
  bottomBar: @Composable () -> Unit,
  onOpenSetup: () -> Unit,
) {
  TabScaffold(
    title = "Status",
    message = state.message,
    onMessageShown = viewModel::messageShown,
    bottomBar = bottomBar,
    actions = {
      IconButton(onClick = onOpenSetup) { Icon(Icons.Default.Settings, contentDescription = "Endpoint") }
    },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      HealthCard(state, viewModel)
      EndpointCard(state, onOpenSetup, viewModel)
    }
  }
}

@Composable
private fun HealthCard(state: UiState, viewModel: ListenerViewModel) {
  val healthy = state.listenerEnabled && state.probe != ListenerProbe.Result.NOT_DELIVERED
  val tip = viewModel.oemTip
  Card(
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (healthy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (healthy) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = null)
        Text(
          // Judul mengikuti kesehatan sesungguhnya, bukan cuma status izin — izin ada tapi
          // service tidak ter-bind adalah justru kasus yang paling menyesatkan.
          when {
            !state.listenerEnabled -> "Pembaca notifikasi mati"
            state.probe == ListenerProbe.Result.NOT_DELIVERED -> "Izin ada, layanan belum jalan"
            else -> "Pembaca notifikasi aktif"
          },
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(start = 8.dp),
        )
      }

      if (!state.listenerEnabled) {
        Text("Tanpa izin ini aplikasi tidak bisa membaca notifikasi apa pun.")
        FilledTonalButton(onClick = viewModel::openListenerSettings) { Text("Beri izin") }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          FilledTonalButton(onClick = viewModel::runProbe, enabled = !state.probeRunning) {
            if (state.probeRunning) CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp))
            Text("Tes sekarang")
          }
          when (state.probe) {
            ListenerProbe.Result.OK -> StatusChip("Hidup", Icons.Default.CheckCircle)
            ListenerProbe.Result.NOT_DELIVERED -> StatusChip("Tidak sampai", Icons.Default.Error)
            ListenerProbe.Result.BLOCKED_BY_OS -> StatusChip("Terblokir", Icons.Default.Warning)
            null -> Unit
          }
        }
        when (state.probe) {
          ListenerProbe.Result.NOT_DELIVERED -> {
            Text(tip.probeNotDeliveredMessage, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilledTonalButton(onClick = viewModel::openAutostartSettings) { Text("Buka Autostart") }
              TextButton(onClick = viewModel::openOemGuide) { Text("Panduan ${tip.vendorLabel}") }
            }
          }
          ListenerProbe.Result.BLOCKED_BY_OS -> Text(tip.probeBlockedMessage, style = MaterialTheme.typography.bodyMedium)
          else -> Unit
        }
      }

      if (!state.batteryExempt) {
        HorizontalDivider()
        Text(
          "Optimasi baterai masih aktif — sistem bisa mematikan aplikasi ini saat layar mati.",
          style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = viewModel::openBatterySettings) { Text("Kecualikan dari optimasi baterai") }
      }
    }
  }
}

@Composable
private fun StatusChip(label: String, icon: ImageVector) {
  AssistChip(onClick = {}, label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) })
}

@Composable
private fun EndpointCard(state: UiState, onOpenSetup: () -> Unit, viewModel: ListenerViewModel) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Endpoint", style = MaterialTheme.typography.titleMedium)
      Text(
        state.endpoint.url.ifBlank { "Belum diatur" },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        "${state.watchedCount} aplikasi dibaca · ${state.catalogSize} gateway di katalog",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onOpenSetup) { Text("Ubah") }
        if (state.pendingCount > 0) {
          TextButton(onClick = viewModel::flushNow) {
            Icon(Icons.Default.Bolt, contentDescription = null)
            Text("Kirim ${state.pendingCount} tertunda", modifier = Modifier.padding(start = 4.dp))
          }
        }
      }
    }
  }
}
