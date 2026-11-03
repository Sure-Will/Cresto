package com.nevoit.cresto.theme

import androidx.compose.runtime.Composable
import com.nevoit.cresto.feature.settings.util.SettingsManager
import com.nevoit.glasense.theme.ThemeMode
import com.nevoit.glasense.theme.resolveDarkTheme

@Composable
fun isAppInDarkTheme(): Boolean {
    val mode = when (SettingsManager.colorModeState.intValue) {
        0 -> ThemeMode.LIGHT
        1 -> ThemeMode.DARK
        2 -> ThemeMode.SYSTEM
        else -> ThemeMode.SYSTEM
    }
    return resolveDarkTheme(mode)
}