package com.saquone.notificationlistener.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(state: UiState, viewModel: ListenerViewModel, onDone: () -> Unit, canGoBack: Boolean) {
  var url by rememberSaveable(state.endpoint.url) { mutableStateOf(state.endpoint.url) }
  var secret by rememberSaveable(state.endpoint.secret) { mutableStateOf(state.endpoint.secret) }
  var secretVisible by rememberSaveable { mutableStateOf(false) }

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
        title = { Text(if (canGoBack) "Endpoint" else "Selamat datang") },
        navigationIcon = {
          if (canGoBack) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        "Notifikasi dari aplikasi yang kamu pilih dikirim sebagai JSON ke alamat ini. " +
          "Tidak ada akun, tidak ada server perantara.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("URL endpoint") },
        placeholder = { Text("https://contoh.com/events") },
        supportingText = {
          Text(
            if (url.startsWith("http://")) "HTTP tanpa enkripsi — aman untuk jaringan lokal, pakai HTTPS di luar itu."
            else "Harus bisa menerima POST JSON."
          )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = secret,
        onValueChange = { secret = it },
        label = { Text("Secret (opsional)") },
        supportingText = { Text("Kalau diisi, tiap request ditandatangani HMAC-SHA256 di header X-Signature.") },
        singleLine = true,
        visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
          IconButton(onClick = { secretVisible = !secretVisible }) {
            Icon(
              if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = if (secretVisible) "Sembunyikan" else "Tampilkan",
            )
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = {
            viewModel.saveEndpoint(url, secret)
            onDone()
          },
          enabled = url.isNotBlank(),
          modifier = Modifier.weight(1f),
        ) {
          Text("Simpan")
        }
        OutlinedButton(
          onClick = { viewModel.sendTest(url, secret) },
          enabled = url.isNotBlank(),
          modifier = Modifier.weight(1f),
        ) {
          Text("Tes kirim")
        }
      }

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Bentuk data yang dikirim", style = MaterialTheme.typography.titleSmall)
          Text(
            """
            POST <URL kamu>
            Content-Type: application/json
            X-Signature: <HMAC-SHA256 hex dari body>

            {
              "package_name": "id.dana",
              "title": "Pembayaran Masuk",
              "text": "Rp50.137 diterima",
              "posted_at": 1765432100000
            }
            """
              .trimIndent(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          )
          Text(
            "posted_at = epoch milidetik. Balas 2xx untuk menandai terkirim; kode lain membuat " +
              "notifikasi tetap di antrean dan dicoba lagi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
