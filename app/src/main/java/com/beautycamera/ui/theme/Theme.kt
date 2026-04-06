package com.beautycamera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PinkAccent = Color(0xFFFF6B9D)
val PinkLight = Color(0xFFFFB3D1)
val PinkDark = Color(0xFFCC3D6E)
val BackgroundDark = Color(0xFF0D0D0D)
val SurfaceDark = Color(0xFF1A1A1A)
val SurfaceVariant = Color(0xFF2A2A2A)
val OnSurface = Color(0xFFE0E0E0)
val OnSurfaceVariant = Color(0xFF9E9E9E)

private val DarkColorScheme = darkColorScheme(
    primary = PinkAccent,
    onPrimary = Color.White,
    primaryContainer = PinkDark,
    onPrimaryContainer = PinkLight,
    secondary = Color(0xFFBB86FC),
    onSecondary = Color.Black,
    background = BackgroundDark,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Color(0xFFCF6679),
    onError = Color.White,
    outline = Color(0xFF444444)
)

@Composable
fun BeautyCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
