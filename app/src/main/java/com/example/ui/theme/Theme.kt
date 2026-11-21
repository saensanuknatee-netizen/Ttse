package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AuraLightColorScheme = lightColorScheme(
    primary = AuraPrimaryBlue,
    onPrimary = AuraOnPrimary,
    primaryContainer = AuraSoftBluePill,
    onPrimaryContainer = AuraOnSoftBlue,
    secondary = AuraSecondarySlate,
    onSecondary = AuraOnPrimary,
    secondaryContainer = AuraSecondaryContainer,
    onSecondaryContainer = AuraOnSecondaryContainer,
    tertiary = AuraTertiaryTeal,
    onTertiary = AuraOnPrimary,
    tertiaryContainer = AuraTertiaryContainer,
    onTertiaryContainer = AuraOnTertiaryContainer,
    background = AuraBackground,
    onBackground = AuraTextPrimary,
    surface = AuraSurface,
    onSurface = AuraTextPrimary,
    surfaceVariant = AuraSurfaceVariant,
    onSurfaceVariant = AuraTextSecondary,
    outline = AuraOutline,
    outlineVariant = AuraOutlineVariant,
    error = AuraErrorRed,
    onError = AuraOnPrimary,
    errorContainer = AuraErrorContainer,
    onErrorContainer = AuraErrorRed
)

private val AuraDarkColorScheme = darkColorScheme(
    primary = AuraDarkPrimary,
    onPrimary = AuraDarkOnPrimary,
    primaryContainer = AuraDarkPrimaryContainer,
    onPrimaryContainer = AuraDarkOnPrimaryContainer,
    secondary = AuraDarkTextSecondary,
    onSecondary = AuraDarkBackground,
    secondaryContainer = AuraDarkSurfaceVariant,
    onSecondaryContainer = AuraDarkTextPrimary,
    tertiary = AuraTertiaryTeal,
    onTertiary = AuraOnPrimary,
    tertiaryContainer = AuraOnTertiaryContainer,
    onTertiaryContainer = AuraTertiaryContainer,
    background = AuraDarkBackground,
    onBackground = AuraDarkTextPrimary,
    surface = AuraDarkSurface,
    onSurface = AuraDarkTextPrimary,
    surfaceVariant = AuraDarkSurfaceVariant,
    onSurfaceVariant = AuraDarkTextSecondary,
    outline = AuraDarkOutline,
    outlineVariant = AuraDarkSurfaceVariant,
    error = AuraErrorRed,
    onError = AuraOnPrimary
)

val AuraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AuraNavTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Default to minimalist crisp white & soft-blue light theme matching reference UX
    val colorScheme = if (darkTheme) AuraDarkColorScheme else AuraLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuraTypography,
        shapes = AuraShapes,
        content = content
    )
}
