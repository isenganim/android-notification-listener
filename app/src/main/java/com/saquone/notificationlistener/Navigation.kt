package com.saquone.notificationlistener

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.saquone.notificationlistener.ui.AppsScreen
import com.saquone.notificationlistener.ui.ListenerViewModel
import com.saquone.notificationlistener.ui.LogsScreen
import com.saquone.notificationlistener.ui.OemGuideScreen
import com.saquone.notificationlistener.ui.SetupScreen
import com.saquone.notificationlistener.ui.StatusScreen
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

private data class Tab(val key: NavKey, val label: String, val icon: ImageVector)

private val TABS =
  listOf(
    Tab(Status, "Status", Icons.Default.MonitorHeart),
    Tab(Apps, "Aplikasi", Icons.Default.Apps),
    Tab(Logs, "Log", Icons.AutoMirrored.Filled.List),
  )

@Composable
fun MainNavigation() {
  val viewModel: ListenerViewModel = viewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val backStack = rememberNavBackStack(Status)

  // Izin bisa berubah di Settings sistem. LifecycleResumeEffect, bukan Activity.onResume: komposisi
  // pertama terjadi setelah onResume pertama, jadi pengecekan di sana terlewat saat app baru dibuka.
  LifecycleResumeEffect(Unit) {
    viewModel.refreshPermissions()
    onPauseOrDispose {}
  }

  LaunchedEffect(state.endpointLoaded) {
    if (state.endpointLoaded && !state.endpoint.isConfigured) backStack.add(Setup)
  }

  val current = backStack.lastOrNull()
  val bottomBar: @Composable () -> Unit = {
    if (TABS.any { it.key == current }) {
      NavigationBar {
        TABS.forEach { tab ->
          NavigationBarItem(
            selected = current == tab.key,
            onClick = {
              // Tab selalu menggantikan tab, tidak menumpuk — back dari tab mana pun keluar app.
              if (current != tab.key) {
                backStack.removeLastOrNull()
                backStack.add(tab.key)
              }
            },
            icon = { Icon(tab.icon, contentDescription = null) },
            label = { Text(tab.label) },
          )
        }
      }
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    transitionSpec = {
      // Material Design 3 Shared Axis (X) Forward Transition
      (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing), initialOffsetX = { (it * 0.15f).toInt() }) +
        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))) togetherWith
        (slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing), targetOffsetX = { -(it * 0.15f).toInt() }) +
          fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)))
    },
    popTransitionSpec = {
      // Material Design 3 Shared Axis (X) Backward Transition
      (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing), initialOffsetX = { -(it * 0.15f).toInt() }) +
        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))) togetherWith
        (slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing), targetOffsetX = { (it * 0.15f).toInt() }) +
          fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)))
    },
    predictivePopTransitionSpec = {
      (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing), initialOffsetX = { -(it * 0.15f).toInt() }) +
        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))) togetherWith
        (slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing), targetOffsetX = { (it * 0.15f).toInt() }) +
          fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)))
    },
    entryProvider =
      entryProvider {
        entry<Status> {
          StatusScreen(
            state,
            viewModel,
            bottomBar,
            onOpenSetup = { backStack.add(Setup) },
            onOpenOemGuide = { url -> backStack.add(OemGuide(url)) },
          )
        }
        entry<OemGuide> { key -> OemGuideScreen(key.url, onBack = { backStack.removeLastOrNull() }) }
        entry<Apps> { AppsScreen(state, viewModel, bottomBar) }
        entry<Logs> { LogsScreen(state, viewModel, bottomBar) }
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
