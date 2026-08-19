package com.saquone.notificationlistener.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(state: UiState, viewModel: ListenerViewModel, bottomBar: @Composable () -> Unit) {
  TabScaffold(
    title = "Aplikasi",
    message = state.message,
    onMessageShown = viewModel::messageShown,
    bottomBar = bottomBar,
    actions = {
      IconButton(onClick = { viewModel.refreshAppsPage() }, enabled = !state.catalogSyncing) {
        if (state.catalogSyncing) CircularProgressIndicator(Modifier.size(20.dp))
        else Icon(Icons.Default.Refresh, contentDescription = "Perbarui katalog")
      }
    },
  ) { padding ->
    PullToRefreshBox(
      isRefreshing = state.isRefreshing,
      onRefresh = { viewModel.refreshAppsPage() },
      modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      if (state.appsLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      } else {
        LazyColumn(
          Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
              Text(
                "Hanya aplikasi pembayaran yang didukung yang muncul di sini. Daftarnya datang dari " +
                  "qris-server dan disimpan permanen ke Room DB HP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              FilledTonalButton(
                onClick = viewModel::updateParserRules,
                enabled = !state.catalogSyncing,
                modifier = Modifier.padding(top = 8.dp),
              ) {
                if (state.catalogSyncing) {
                  CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp))
                } else {
                  Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text("Update Parser & Simpan ke Room", modifier = Modifier.padding(start = 6.dp))
              }
            }
          }

          val (installed, missing) = state.apps.partition { it.installed }

          items(installed, key = { it.packageName }) { app -> AppRow(app, viewModel) }

          if (missing.isNotEmpty()) {
            item {
              Text(
                "Belum terpasang di HP ini",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
              )
            }
            items(missing, key = { it.packageName }) { app -> AppRow(app, viewModel) }
          }
        }
      }
    }
  }
}

@Composable
private fun AppRow(app: GatewayApp, viewModel: ListenerViewModel) {
  ListItem(
    headlineContent = {
      Text(
        app.gatewayLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color =
          if (app.installed) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    supportingContent = {
      Text(
        if (app.verified) app.packageName else "${app.packageName} · pola belum diverifikasi",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    trailingContent = {
      Switch(
        checked = app.watched,
        enabled = app.installed,
        onCheckedChange = { viewModel.setWatched(app.packageName, it) },
      )
    },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
  )
}
