package com.example.ui.theme

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
  darkColorScheme(primary = PrimaryBlue, secondary = SecondaryBlue, tertiary = AccentGreen, background = DarkBackground, surface = DarkSurface)

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryBlue,
    secondary = SecondaryBlue,
    tertiary = AccentGreen,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Force light theme
  dynamicColor: Boolean = false, // Don't use dynamic colors to keep it consistent
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = SecondaryBlue,
    tertiary = AccentGreen,
    background = androidx.compose.ui.graphics.Color.White,
    surface = androidx.compose.ui.graphics.Color.White
  ), typography = Typography, content = content)
}
