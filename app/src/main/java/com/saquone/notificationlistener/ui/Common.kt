package com.saquone.notificationlistener.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/** Kerangka bersama tiga tab: judul, aksi, bottom bar, dan snackbar pesan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScaffold(
  title: String,
  message: String?,
  onMessageShown: () -> Unit,
  bottomBar: @Composable () -> Unit,
  actions: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  val snackbar = remember { SnackbarHostState() }
  LaunchedEffect(message) {
    message?.let {
      snackbar.showSnackbar(it)
      onMessageShown()
    }
  }
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHost = { SnackbarHost(snackbar) },
    bottomBar = bottomBar,
    topBar = { TopAppBar(title = { Text(title) }, actions = { actions() }, scrollBehavior = scrollBehavior) },
    content = content,
  )
}
