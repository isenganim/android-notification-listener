package com.saquone.notificationlistener.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Panduan dontkillmyapp.com ditampilkan di dalam app — pengguna tak perlu balik dari browser. */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OemGuideScreen(url: String, onBack: () -> Unit) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Panduan OEM") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
        },
      )
    }
  ) { padding ->
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        WebView(context).apply {
          settings.javaScriptEnabled = true
          loadUrl(url)
        }
      },
      update = { it.loadUrl(url) },
    )
  }
}
