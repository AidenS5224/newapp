package com.gamerconnect.testclient

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GamerConnectColors = darkColorScheme(
    primary = Color(0xFF8B2EFF),
    background = Color(0xFF030711),
    surface = Color(0xFF0B1220),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun GamerConnectTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GamerConnectColors,
        content = content
    )
}