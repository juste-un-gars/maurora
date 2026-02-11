/**
 * @file AuroraTheme.kt
 * @description Material3 theme with dark/light mode following system setting.
 */
package com.franck.aurorawidget.ui.theme

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

private val AuroraDark = darkColorScheme(
    primary = Color(0xFF4CAF50),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF121218),
    surface = Color(0xFF1B1B2F),
    surfaceVariant = Color(0xFF2A2A3E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFB0BEC5)
)

private val AuroraLight = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF00897B),
    background = Color(0xFFF5F5FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EAF6),
    onBackground = Color(0xFF1B1B2F),
    onSurface = Color(0xFF1B1B2F),
    onSurfaceVariant = Color(0xFF5C6370)
)

@Composable
fun AuroraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ dynamic colors from wallpaper
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> AuroraDark
        else -> AuroraLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
