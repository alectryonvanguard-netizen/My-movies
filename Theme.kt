package com.mymovie.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// AMOLED-friendly palette: true black background, charcoal surfaces, violet accent.
val Black = Color(0xFF000000)
val Charcoal = Color(0xFF101017)
val CardColor = Color(0xFF1A1A24)
val Violet = Color(0xFF7C3AED)
val VioletSoft = Color(0xFFA78BFA)
val Pink = Color(0xFFEC4899)
val Outline = Color(0xFF2B2B3A)
val TextDim = Color(0xFFA3A3B5)
val BrandBrush = Brush.horizontalGradient(listOf(Violet, Color(0xFF4F46E5)))

private val Scheme = darkColorScheme(
    primary = Violet, onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1D8F), onPrimaryContainer = Color.White,
    secondary = Pink, onSecondary = Color.White,
    background = Black, onBackground = Color(0xFFF4F4F8),
    surface = Charcoal, onSurface = Color(0xFFF4F4F8),
    surfaceVariant = CardColor, onSurfaceVariant = TextDim,
    outline = Outline, outlineVariant = Outline,
    error = Color(0xFFFF5A5F)
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
fun MyMovieTheme(content: @Composable () -> Unit) {
    // Always dark: cinematic AMOLED look.
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
