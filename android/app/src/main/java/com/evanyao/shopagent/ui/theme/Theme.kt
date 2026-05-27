package com.evanyao.shopagent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onPrimary = Surface,
    onSecondary = Surface,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
