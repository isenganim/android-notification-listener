package com.saquone.notificationlistener

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.saquone.notificationlistener.theme.SaquoneNotificationListenerTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Dibutuhkan ListenerProbe untuk mem-post notifikasi tesnya sendiri.
    val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    enableEdgeToEdge()
    setContent {
      SaquoneNotificationListenerTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() }
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
}
