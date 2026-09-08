package com.cardovia.merkon.app.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = MerkonFreshGreen,
    onPrimary = Color.Black,
    primaryContainer = MerkonDeepMarketGreen,
    onPrimaryContainer = Color.White,
    secondary = MerkonMarketOrange,
    onSecondary = Color.Black,
    secondaryContainer = MerkonDarkMarketOrangeContainer,
    onSecondaryContainer = MerkonMarketOrangeContainer,
    tertiary = MerkonWarmYellow,
    onTertiary = Color.Black,
    tertiaryContainer = MerkonDarkWarmYellowContainer,
    onTertiaryContainer = MerkonWarmYellowContainer,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = MerkonDeepMarketGreen,
    onPrimary = Color.White,
    primaryContainer = MerkonDeepMarketGreenContainer,
    onPrimaryContainer = MerkonOnDeepMarketGreenContainer,
    secondary = MerkonMarketOrange,
    onSecondary = Color.Black,
    secondaryContainer = MerkonMarketOrangeContainer,
    onSecondaryContainer = MerkonOnMarketOrangeContainer,
    tertiary = MerkonWarmYellow,
    onTertiary = Color.Black,
    tertiaryContainer = MerkonWarmYellowContainer,
    onTertiaryContainer = MerkonOnWarmYellowContainer,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun MerkonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
