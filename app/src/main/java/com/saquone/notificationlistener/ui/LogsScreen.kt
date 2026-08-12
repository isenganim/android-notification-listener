package com.saquone.notificationlistener.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saquone.notificationlistener.data.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(state: UiState, viewModel: ListenerViewModel, bottomBar: @Composable () -> Unit) {
  TabScaffold(
    title = "Log",
    message = state.message,
    onMessageShown = viewModel::messageShown,
    bottomBar = bottomBar,
    actions = {
      if (state.events.isNotEmpty()) {
        IconButton(onClick = viewModel::clearLog) { Icon(Icons.Default.Delete, contentDescription = "Bersihkan") }
      }
    },
  ) { padding ->
    if (state.events.isEmpty()) {
      Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
          "Belum ada notifikasi yang tertangkap.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      return@TabScaffold
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
      items(state.events, key = { it.id }) { EventRow(it) }
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
    overlineContent = {
      Text(
        TIME_FORMAT.format(Date(event.postedAt)) +
          (event.amount?.let { " · Rp %,d".format(it) } ?: " · nominal tak terbaca")
      )
    },
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
