package com.flowframe.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = FlowPrimaryLight,
    onPrimary = FlowOnPrimaryLight,
    primaryContainer = FlowPrimaryContainerLight,
    onPrimaryContainer = FlowOnPrimaryContainerLight,
    secondary = FlowSecondaryLight,
    onSecondary = FlowOnSecondaryLight,
    secondaryContainer = FlowSecondaryContainerLight,
    onSecondaryContainer = FlowOnSecondaryContainerLight,
    tertiary = FlowTertiaryLight,
    background = FlowBackgroundLight,
    surface = FlowSurfaceLight,
    surfaceVariant = FlowSurfaceVariantLight,
    onSurface = FlowOnSurfaceLight,
    onSurfaceVariant = FlowOnSurfaceVariantLight,
    outline = FlowOutlineLight,
    error = FlowErrorLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = FlowPrimaryDark,
    onPrimary = FlowOnPrimaryDark,
    primaryContainer = FlowPrimaryContainerDark,
    onPrimaryContainer = FlowOnPrimaryContainerDark,
    secondary = FlowSecondaryDark,
    onSecondary = FlowOnSecondaryDark,
    secondaryContainer = FlowSecondaryContainerDark,
    onSecondaryContainer = FlowOnSecondaryContainerDark,
    tertiary = FlowTertiaryDark,
    background = FlowBackgroundDark,
    surface = FlowSurfaceDark,
    surfaceVariant = FlowSurfaceVariantDark,
    onSurface = FlowOnSurfaceDark,
    onSurfaceVariant = FlowOnSurfaceVariantDark,
    outline = FlowOutlineDark,
    error = FlowErrorDark,
)

private val FlowFrameShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FlowFrameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FlowFrameTypography,
        shapes = FlowFrameShapes,
        content = content,
    )
}
