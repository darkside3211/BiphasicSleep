package com.antigravity.biphasicsleep.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Biometric Dark Palette (Oura / Whoop / Apple Sleep Inspired)
val ObsidianBackground = Color(0xFF06090F)
val ObsidianSurface = Color(0xFF0D1422)
val ObsidianCard = Color(0xFF131D2F)
val ObsidianCardElevated = Color(0xFF19253B)
val ObsidianBorder = Color(0xFF23324C)
val ObsidianBorderSubtle = Color(0xFF1A263B)

val AnchorCyan = Color(0xFF38BDF8)
val AnchorCyanDim = Color(0xFF0284C7)
val CircadianAmber = Color(0xFFF59E0B)
val CircadianAmberDim = Color(0xFFB45309)
val LockoutCrimson = Color(0xFFF43F5E)
val LockoutCrimsonBg = Color(0xFF28101A)
val VerifiedEmerald = Color(0xFF10B981)
val VerifiedEmeraldBg = Color(0xFF0B261D)

val MutedSlate = Color(0xFF64748B)
val SoftSilver = Color(0xFF94A3B8)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFFCBD5E1)

private val CircadianDarkColorScheme = darkColorScheme(
    primary = AnchorCyan,
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF0C2C3D),
    onPrimaryContainer = AnchorCyan,
    secondary = CircadianAmber,
    onSecondary = Color(0xFF261900),
    secondaryContainer = Color(0xFF362406),
    onSecondaryContainer = CircadianAmber,
    tertiary = VerifiedEmerald,
    onTertiary = Color(0xFF002111),
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    error = LockoutCrimson,
    onError = Color.White
)

val BiometricTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = "tnum"
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = "tnum"
    )
)

@Composable
fun BiphasicSleepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CircadianDarkColorScheme,
        typography = BiometricTypography,
        content = content
    )
}
