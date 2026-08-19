package com.saquone.notificationlistener.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.saquone.notificationlistener.data.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(state: UiState, viewModel: ListenerViewModel, bottomBar: @Composable () -> Unit) {
  val listState = rememberLazyListState()

  val shouldLoadMore = remember {
    derivedStateOf {
      val totalItems = listState.layoutInfo.totalItemsCount
      val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      totalItems > 0 && lastVisibleItem >= totalItems - 3
    }
  }

  LaunchedEffect(shouldLoadMore.value) {
    if (shouldLoadMore.value && state.hasMoreEvents) {
      viewModel.loadMoreEvents()
    }
  }

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
    PullToRefreshBox(
      isRefreshing = state.isRefreshing,
      onRefresh = { viewModel.refreshLogs() },
      modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      if (state.events.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
          Text(
            "Belum ada notifikasi yang tertangkap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 16.dp),
        ) {
          items(state.events, key = { it.id }) { EventRow(it) }
        }
      }
    }
  }
}

@Composable
private fun EventRow(event: Event) {
  ListItem(
    leadingContent = { AppIcon(event.pkg) },
    overlineContent = { Text(TIME_FORMAT.format(Date(event.postedAt))) },
    headlineContent = {
      Text(
        listOf(event.title, event.text).filter { it.isNotBlank() }.joinToString(" · "),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    },
    supportingContent = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
      ) {
        // Dua hal berbeda yang sering tertukar: nominal TERBACA, dan pembayaran TERVERIFIKASI.
        if (event.amount != null) {
          Chip("Rp %,d".format(event.amount), null, MaterialTheme.colorScheme.primaryContainer)
        } else {
          Chip("Nominal tak terbaca", Icons.Default.HelpOutline, MaterialTheme.colorScheme.surfaceVariant)
        }
        when {
          event.verified ->
            Chip("Terverifikasi", Icons.Default.Verified, MaterialTheme.colorScheme.tertiaryContainer)
          event.status == Event.SENT && event.amount != null ->
            Chip("Tanpa tagihan cocok", null, MaterialTheme.colorScheme.surfaceVariant)
          event.status == Event.FAILED ->
            Chip("Gagal kirim", null, MaterialTheme.colorScheme.errorContainer)
          event.status != Event.SENT ->
            Chip("Menunggu kirim", Icons.Default.HourglassEmpty, MaterialTheme.colorScheme.surfaceVariant)
        }
      }
    },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
  )
}

@Composable
private fun Chip(label: String, icon: ImageVector?, container: Color) {
  SuggestionChip(
    onClick = {},
    enabled = false,
    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    icon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) } },
    colors = SuggestionChipDefaults.suggestionChipColors(disabledContainerColor = container),
  )
}

/** Ikon aplikasi sumber notifikasi. Butuh `<queries>` LAUNCHER di manifest agar terlihat. */
@Composable
private fun AppIcon(pkg: String) {
  val context = LocalContext.current
  val icon by
    produceState<ImageBitmap?>(initialValue = null, key1 = pkg) {
      value =
        withContext(Dispatchers.IO) {
          // Adaptive icon bukan BitmapDrawable, jadi harus dirender dulu ke bitmap.
          runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
  val bitmap = icon
  if (bitmap != null) {
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
  } else {
    Icon(
      Icons.Default.Android,
      contentDescription = null,
      modifier = Modifier.size(40.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private val TIME_FORMAT = SimpleDateFormat("dd MMM HH:mm:ss", Locale.forLanguageTag("id-ID"))
