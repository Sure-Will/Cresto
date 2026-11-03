package com.nevoit.cresto.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class ThemeSettings(
    val liquidGlass: Boolean,
    val liteMode: Boolean,
    val dynamicColor: Boolean
)

val LocalThemeSettings = staticCompositionLocalOf {
    ThemeSettings(
        liquidGlass = false,
        liteMode = false,
        dynamicColor = false
    )
}