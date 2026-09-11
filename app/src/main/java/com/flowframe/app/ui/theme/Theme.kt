package com.flowframe.app.ui.theme

import android.os.Build
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

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
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F1F8),
    surfaceContainerHigh = Color(0xFFEAECF5),
    surfaceContainerHighest = Color(0xFFE3E6F0),
    outlineVariant = Color(0xFFDADDEA),
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
    surfaceContainerLowest = Color(0xFF0B0D14),
    surfaceContainerLow = Color(0xFF151923),
    surfaceContainer = Color(0xFF1C2130),
    surfaceContainerHigh = Color(0xFF252B3B),
    surfaceContainerHighest = Color(0xFF303749),
    outlineVariant = Color(0xFF363E52),
)

private val FlowFrameShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun FlowFrameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
    else -> null
}
