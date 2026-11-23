package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val KyaathiLightColorScheme = lightColorScheme(
    primary = KyaathiPurplePrimary,
    onPrimary = KyaathiWhite,
    primaryContainer = KyaathiPurpleLight,
    onPrimaryContainer = KyaathiPurpleDark,
    secondary = KyaathiVioletSecondary,
    onSecondary = KyaathiWhite,
    secondaryContainer = KyaathiVioletContainer,
    onSecondaryContainer = KyaathiPurpleDark,
    tertiary = KyaathiEmerald,
    onTertiary = KyaathiWhite,
    tertiaryContainer = KyaathiEmeraldContainer,
    onTertiaryContainer = KyaathiEmerald,
    background = KyaathiSurfaceWhite,
    onBackground = KyaathiTextPrimary,
    surface = KyaathiWhite,
    onSurface = KyaathiTextPrimary,
    surfaceVariant = KyaathiSurfaceTint,
    onSurfaceVariant = KyaathiTextSecondary,
    outline = KyaathiOutline,
    outlineVariant = KyaathiPurpleSoft,
    error = KyaathiCrimson,
    onError = KyaathiWhite,
    errorContainer = KyaathiCrimsonContainer,
    onErrorContainer = KyaathiCrimson
)

private val KyaathiDarkColorScheme = darkColorScheme(
    primary = KyaathiDarkPrimary,
    onPrimary = KyaathiDarkOnPrimary,
    primaryContainer = KyaathiDarkPrimaryContainer,
    onPrimaryContainer = KyaathiPurpleLight,
    secondary = KyaathiVioletSecondary,
    onSecondary = KyaathiWhite,
    secondaryContainer = KyaathiDarkSurfaceVariant,
    onSecondaryContainer = KyaathiPurpleLight,
    tertiary = KyaathiEmerald,
    onTertiary = KyaathiWhite,
    background = KyaathiDarkBackground,
    onBackground = KyaathiDarkTextPrimary,
    surface = KyaathiDarkSurface,
    onSurface = KyaathiDarkTextPrimary,
    surfaceVariant = KyaathiDarkSurfaceVariant,
    onSurfaceVariant = KyaathiDarkTextSecondary,
    outline = KyaathiDarkOutline,
    error = KyaathiCrimson,
    onError = KyaathiWhite
)

val KyaathiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KyaathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KyaathiDarkColorScheme else KyaathiLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KyaathiTypography,
        shapes = KyaathiShapes,
        content = content
    )
}
