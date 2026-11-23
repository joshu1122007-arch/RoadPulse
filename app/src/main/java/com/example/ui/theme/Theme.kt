package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = SurfaceWhite,
    primaryContainer = PrimaryBlueContainer,
    onPrimaryContainer = DeepNavy,
    secondary = DeepNavy,
    onSecondary = SurfaceWhite,
    secondaryContainer = SurfaceVariantSlate,
    onSecondaryContainer = TextPrimary,
    tertiary = RiskModerateAmber,
    onTertiary = SurfaceWhite,
    tertiaryContainer = RiskModerateContainer,
    onTertiaryContainer = TextPrimary,
    background = BackgroundSlate,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantSlate,
    onSurfaceVariant = TextSecondary,
    outline = BorderSlate,
    outlineVariant = BorderSlate,
    error = RiskHighRed,
    onError = SurfaceWhite,
    errorContainer = RiskHighContainer,
    onErrorContainer = RiskHighRed
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = DeepNavyDarker,
    primaryContainer = Color(0xFF10495E),
    onPrimaryContainer = Color(0xFFD6EEF5),
    secondary = Color(0xFF93C5FD),
    onSecondary = DeepNavyDarker,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = Color(0xFFFBBF24),
    onTertiary = DeepNavyDarker,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = Color(0xFFEF4444),
    onError = SurfaceWhite,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFEE2E2)
)

val RoadPulseShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun RoadPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RoadPulseTypography,
        shapes = RoadPulseShapes,
        content = content
    )
}
