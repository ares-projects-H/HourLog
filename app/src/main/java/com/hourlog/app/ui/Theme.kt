package com.hourlog.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import com.hourlog.app.domain.Appearance
import com.hourlog.app.domain.Preferences

@Composable fun HourLogTheme(preferences: Preferences, content: @Composable () -> Unit) {
    val dark = when (preferences.appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.DARK -> true
        Appearance.LIGHT -> false
    }
    val colors = remember(preferences.colorSeed, dark) { hourLogColors(preferences.colorSeed, dark) }
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
