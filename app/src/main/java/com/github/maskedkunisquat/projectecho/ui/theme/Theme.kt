package com.github.maskedkunisquat.projectecho.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EchoColorScheme = darkColorScheme(
    primary          = EchoAmber,
    onPrimary        = EchoBackground,
    primaryContainer = EchoAmberDim,
    secondary        = EchoGold,
    onSecondary      = EchoBackground,
    background       = EchoBackground,
    onBackground     = EchoOnBackground,
    surface          = EchoSurface,
    onSurface        = EchoOnSurface,
    surfaceVariant   = EchoSurfaceVariant,
    onSurfaceVariant = EchoOnSurfaceMuted,
    outline          = EchoOnSurfaceMuted,
)

@Composable
fun ProjectEchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EchoColorScheme,
        typography  = Typography,
        content     = content,
    )
}
