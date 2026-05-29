package de.openbahn.navigator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DbRed = Color(0xFFEC0016)
private val DbDark = Color(0xFF1A1A1A)
private val DbSurface = Color(0xFFF5F5F7)

private val LightColors = lightColorScheme(
    primary = DbRed,
    onPrimary = Color.White,
    secondary = DbDark,
    background = DbSurface,
    surface = Color.White,
    surfaceVariant = Color(0xFFE8E8ED),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF4D5E),
    onPrimary = Color.Black,
    secondary = Color(0xFFB0B0B8),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
)

@Composable
fun OpenBahnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
