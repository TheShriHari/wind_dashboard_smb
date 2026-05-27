package com.aeromdc.aeromonitor.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// -----------------------------------------------------------------------
// Exact color palette from the web dashboard CSS
// -----------------------------------------------------------------------
val Primary          = Color(0xFFBEC6E0)   // #bec6e0
val OnPrimary        = Color(0xFF283044)   // #283044
val PrimaryContainer = Color(0xFF0F172A)   // #0f172a
val OnPrimaryContainer = Color(0xFF798098) // #798098

val Secondary        = Color(0xFFBCC7DE)   // #bcc7de
val OnSecondary      = Color(0xFF263143)   // #263143
val SecondaryContainer = Color(0xFF3E495D) // #3e495d
val OnSecondaryContainer = Color(0xFFAEB9D0) // #aeb9d0

val Tertiary         = Color(0xFFDEC29A)   // #dec29a
val OnTertiary       = Color(0xFF3E2D11)   // #3e2d11
val TertiaryContainer = Color(0xFF231500)  // #231500

val Error            = Color(0xFFFFB4AB)   // #ffb4ab
val OnError          = Color(0xFF690005)   // #690005
val ErrorContainer   = Color(0xFF93000A)   // #93000a
val OnErrorContainer = Color(0xFFFFDAD6)   // #ffdad6

val Surface          = Color(0xFF131315)   // #131315
val OnSurface        = Color(0xFFE4E2E4)   // #e4e2e4
val SurfaceVariant   = Color(0xFF353436)   // #353436
val OnSurfaceVariant = Color(0xFFC6C6CD)   // #c6c6cd
val SurfaceContainer = Color(0xFF1F1F21)   // #1f1f21
val SurfaceContainerHigh = Color(0xFF2A2A2B) // #2a2a2b
val SurfaceContainerHighest = Color(0xFF353436) // #353436

val Outline          = Color(0xFF909097)   // #909097
val OutlineVariant   = Color(0xFF45464D)   // #45464d

// Semantic colors for status
val ColorOperational = Color(0xFF10B981)   // emerald-500
val ColorFailure     = Color(0xFFEF4444)   // red-500
val ColorMaintenance = Color(0xFFF97316)   // orange-500
val ColorOffline     = Color(0xFFFB7185)   // rose-400
val ColorUnknown     = Color(0xFF6B7280)   // gray-500

val AeroMonitorColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    background = Surface,
    onBackground = OnSurface,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
)

val AeroMonitorTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = (-0.02).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.05.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.05.sp,
    ),
)

@Composable
fun AeroMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AeroMonitorColorScheme,
        typography = AeroMonitorTypography,
        content = content,
    )
}
