package com.ascend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import com.ascend.app.data.prefs.Settings
import com.ascend.app.data.prefs.ThemeMode
import com.ascend.app.ui.navigation.AscendRoot
import com.ascend.app.ui.theme.AscendTheme
import com.ascend.app.ui.theme.Palette

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AscendApp).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = Settings())
            val dark = settings.themeMode != ThemeMode.LIGHT
            LaunchedEffect(settings.themeMode) {
                val style = if (settings.themeMode == ThemeMode.SYSTEM) SystemBarStyle.auto(0, 0)
                else if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, Palette.Ink.toArgb())
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AscendTheme(settings.themeMode) {
                val haptics = if (settings.haptics) LocalHapticFeedback.current else NoHaptics
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    AscendRoot(calculateWindowSizeClass(this).widthSizeClass)
                }
            }
        }
    }
}

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
