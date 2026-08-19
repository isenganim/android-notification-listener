package com.saquone.notificationlistener.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = M3Blue80,
    onPrimary = M3Blue20,
    primaryContainer = M3Blue30,
    onPrimaryContainer = M3Blue90,
    secondary = M3Secondary80,
    onSecondary = M3Secondary20,
    secondaryContainer = M3Secondary30,
    onSecondaryContainer = M3Secondary90,
    tertiary = M3Tertiary80,
    onTertiary = M3Tertiary20,
    tertiaryContainer = M3Tertiary30,
    onTertiaryContainer = M3Tertiary90,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = M3Blue40,
    onPrimary = M3White,
    primaryContainer = M3Blue90,
    onPrimaryContainer = M3Blue10,
    secondary = M3Secondary40,
    onSecondary = M3White,
    secondaryContainer = M3Secondary90,
    onSecondaryContainer = M3Secondary10,
    tertiary = M3Tertiary40,
    onTertiary = M3White,
    tertiaryContainer = M3Tertiary90,
    onTertiaryContainer = M3Tertiary10,
  )

@Composable
fun SaquoneNotificationListenerTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
