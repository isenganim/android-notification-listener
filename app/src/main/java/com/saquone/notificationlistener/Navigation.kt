package com.saquone.notificationlistener

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.saquone.notificationlistener.ui.HomeScreen
import com.saquone.notificationlistener.ui.ListenerViewModel
import com.saquone.notificationlistener.ui.SetupScreen

@Composable
fun MainNavigation() {
  val viewModel: ListenerViewModel = viewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val backStack = rememberNavBackStack(Home)

  // Izin bisa berubah di Settings sistem. LifecycleResumeEffect, bukan Activity.onResume: komposisi
  // pertama terjadi setelah onResume pertama, jadi pengecekan di sana terlewat saat app baru dibuka.
  LifecycleResumeEffect(Unit) {
    viewModel.refreshPermissions()
    onPauseOrDispose {}
  }

  // Belum ada endpoint → langsung ke Setup.
  LaunchedEffect(state.endpointLoaded) {
    if (state.endpointLoaded && !state.endpoint.isConfigured) backStack.add(Setup)
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Home> { HomeScreen(state, viewModel, onOpenSetup = { backStack.add(Setup) }) }
        entry<Setup> {
          SetupScreen(
            state = state,
            viewModel = viewModel,
            onDone = { backStack.removeLastOrNull() },
            canGoBack = state.endpoint.isConfigured,
          )
        }
      },
  )
}
