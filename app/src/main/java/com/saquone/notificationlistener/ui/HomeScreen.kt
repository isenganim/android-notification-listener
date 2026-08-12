package com.saquone.notificationlistener.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saquone.notificationlistener.data.Event
import com.saquone.notificationlistener.util.ListenerProbe
import com.saquone.notificationlistener.util.OemGuidance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, viewModel: ListenerViewModel, onOpenSetup: () -> Unit) {
  val snackbar = remember { SnackbarHostState() }
  LaunchedEffect(state.message) {
    state.message?.let {
      snackbar.showSnackbar(it)
      viewModel.messageShown()
    }
  }
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHost = { SnackbarHost(snackbar) },
    topBar = {
      TopAppBar(
        title = { Text("Notification Listener") },
        actions = {
          IconButton(onClick = onOpenSetup) { Icon(Icons.Default.Settings, contentDescription = "Endpoint") }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item { StatusCard(state, viewModel.oemTip, viewModel) }
      item { EndpointCard(state, onOpenSetup, viewModel::flushNow) }
      item { SectionHeader("Aplikasi yang dibaca", "${state.watchedCount} dipilih") }

      if (state.appsLoading) {
        item {
          Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
      } else {
        items(state.apps, key = { it.packageName }) { app ->
          ListItem(
            headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
              Switch(checked = app.watched, onCheckedChange = { viewModel.setWatched(app.packageName, it) })
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
          )
        }
      }

      item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          SectionHeader("Log terakhir", null, Modifier.weight(1f))
          if (state.events.isNotEmpty()) {
            TextButton(onClick = viewModel::clearLog) {
              Icon(Icons.Default.Delete, contentDescription = null)
              Text("Bersihkan", modifier = Modifier.padding(start = 4.dp))
            }
          }
        }
      }

      if (state.events.isEmpty()) {
        item {
          Text(
            "Belum ada notifikasi yang tertangkap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        items(state.events, key = { it.id }) { EventRow(it) }
      }
    }
  }
}

@Composable
private fun SectionHeader(title: String, trailing: String?, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    if (trailing != null) {
      Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun StatusCard(state: UiState, oemTip: OemGuidance.OemTip, viewModel: ListenerViewModel) {
  val healthy = state.listenerEnabled && state.probe != ListenerProbe.Result.NOT_DELIVERED
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
          if (state.listenerEnabled) "Pembaca notifikasi aktif" else "Pembaca notifikasi mati",
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
            if (state.probeRunning) {
              CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp))
            }
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
            Text(oemTip.probeNotDeliveredMessage, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilledTonalButton(onClick = viewModel::openAutostartSettings) { Text("Buka Autostart") }
              TextButton(onClick = viewModel::openOemGuide) { Text("Panduan ${oemTip.vendorLabel}") }
            }
          }
          ListenerProbe.Result.BLOCKED_BY_OS ->
            Text(oemTip.probeBlockedMessage, style = MaterialTheme.typography.bodyMedium)
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
private fun EndpointCard(state: UiState, onOpenSetup: () -> Unit, onFlush: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Endpoint", style = MaterialTheme.typography.titleMedium)
      Text(
        state.endpoint.url.ifBlank { "Belum diatur" },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onOpenSetup) { Text("Ubah") }
        if (state.pendingCount > 0) {
          TextButton(onClick = onFlush) {
            Icon(Icons.Default.Bolt, contentDescription = null)
            Text("Kirim ${state.pendingCount} tertunda", modifier = Modifier.padding(start = 4.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun EventRow(event: Event) {
  val color =
    when (event.status) {
      Event.SENT -> MaterialTheme.colorScheme.primary
      Event.FAILED -> MaterialTheme.colorScheme.error
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
  ListItem(
    overlineContent = { Text(TIME_FORMAT.format(Date(event.postedAt))) },
    headlineContent = {
      Text(
        listOf(event.title, event.text).filter { it.isNotBlank() }.joinToString(" · "),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    },
    supportingContent = {
      val status =
        when (event.status) {
          Event.SENT -> "terkirim"
          Event.FAILED -> "gagal (${event.lastError ?: "?"}), percobaan ${event.attempts}"
          else -> "menunggu kirim"
        }
      Text("${event.pkg} · $status", color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
  )
}

private val TIME_FORMAT = SimpleDateFormat("dd MMM HH:mm:ss", Locale.forLanguageTag("id-ID"))
