package com.kitchensync360.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Utilitarian dark slate palette aligned with web-dashboard
val KsBackground = Color(0xFF090B0A)
val KsSurface = Color(0xFF101413)
val KsSurfaceElevated = Color(0xFF161B19)
val KsSurfaceSubtle = Color(0xFF131917)
val KsBorder = Color(0xFF212926)
val KsBorderStrong = Color(0xFF2E3834)

val KsGreenPrimary = Color(0xFF16A34A)
val KsGreenSurface = Color(0xFF102618)
val KsGreenText = Color(0xFF4ADE80)

val KsAmberWarning = Color(0xFFD97706)
val KsAmberSurface = Color(0xFF261C0E)
val KsAmberText = Color(0xFFFBBF24)

val KsBlueInfo = Color(0xFF2563EB)
val KsBlueSurface = Color(0xFF111E36)
val KsBlueText = Color(0xFF60A5FA)

val KsRedDanger = Color(0xFFDC2626)
val KsRedSurface = Color(0xFF291415)
val KsRedText = Color(0xFFF87171)

val KsTextPrimary = Color(0xFFF1F5F3)
val KsTextSecondary = Color(0xFF9CAEA7)
val KsTextMuted = Color(0xFF63736D)

// Station header accents
val StationGrillAccent = Color(0xFFE05246)
val StationFryerAccent = Color(0xFFE09F24)
val StationPastryAccent = Color(0xFF9B6B9E)

private val KitchenSyncColorScheme = darkColorScheme(
    primary = KsGreenPrimary,
    onPrimary = Color.White,
    secondary = KsBlueInfo,
    onSecondary = Color.White,
    tertiary = KsAmberWarning,
    background = KsBackground,
    onBackground = KsTextPrimary,
    surface = KsSurface,
    onSurface = KsTextPrimary,
    surfaceVariant = KsSurfaceElevated,
    onSurfaceVariant = KsTextSecondary,
    outline = KsBorder,
    error = KsRedDanger,
    onError = Color.White
)

private val KitchenSyncTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.5).sp,
        color = KsTextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
        color = KsTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        color = KsTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = KsTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        color = KsTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        color = KsTextSecondary
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        color = KsTextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = KsTextSecondary
    )
)

@Composable
fun KitchenSync360Theme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KitchenSyncColorScheme,
        typography = KitchenSyncTypography,
        content = content
    )
}
