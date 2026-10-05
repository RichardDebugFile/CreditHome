package com.alfas.amortizacioncredito.ui.theme

import android.app.Activity
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
    primary = BlueLight, // Azul cielo suave y ligero (no chillón)
    onPrimary = Slate900,
    primaryContainer = Slate800, // Slate 800 - Fondo oscuro elegante y descansado
    onPrimaryContainer = Slate100,
    secondary = CreditHomeEmeraldLight,
    onSecondary = Slate900,
    secondaryContainer = EmeraldDarkContainer,
    onSecondaryContainer = EmeraldBadgeLight,
    tertiary = Amber400,
    background = Slate900,
    surface = Slate800,
    surfaceVariant = Slate700,
    onBackground = Slate50,
    onSurface = Slate50,
    onSurfaceVariant = Slate400,
    outline = Slate600,
    outlineVariant = Slate700
)

private val LightColorScheme = lightColorScheme(
    primary = CreditHomeBlue,
    onPrimary = Color.White,
    primaryContainer = BlueBadgeBg,
    onPrimaryContainer = CreditHomeBlueDark,
    secondary = CreditHomeEmeraldDark,
    onSecondary = Color.White,
    secondaryContainer = GreenBadgeBg,
    onSecondaryContainer = GreenBadgeText,
    tertiary = AmberWarningDark,
    background = Slate50,
    surface = Color.White,
    surfaceVariant = Slate100,
    onBackground = Slate900,
    onSurface = Slate900,
    onSurfaceVariant = Slate600,
    outline = Slate400,
    outlineVariant = Slate200
)

@Composable
fun AmortizacionCreditoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Priorizar la identidad de marca CreditHome
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