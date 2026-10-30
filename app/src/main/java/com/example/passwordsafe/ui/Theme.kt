package com.example.passwordsafe.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Mint = Color(0xFF3DDC97)
val MintDark = Color(0xFF0E9F6E)
val Navy = Color(0xFF0E1320)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF00281A),
    primaryContainer = Color(0xFF16402F),
    onPrimaryContainer = Color(0xFFB4F5D8),
    secondary = Color(0xFF8AB4FF),
    onSecondary = Color(0xFF0A1F44),
    secondaryContainer = Color(0xFF223252),
    onSecondaryContainer = Color(0xFFD9E3FF),
    background = Navy,
    onBackground = Color(0xFFE4E8F2),
    surface = Navy,
    onSurface = Color(0xFFE4E8F2),
    surfaceVariant = Color(0xFF1F2738),
    onSurfaceVariant = Color(0xFFA9B2C6),
    surfaceContainerLowest = Color(0xFF0A0F19),
    surfaceContainerLow = Color(0xFF141B2A),
    surfaceContainer = Color(0xFF182032),
    surfaceContainerHigh = Color(0xFF1E283C),
    surfaceContainerHighest = Color(0xFF253046),
    outline = Color(0xFF465170),
    outlineVariant = Color(0xFF2A3348),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = MintDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F4E0),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF3657B8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE5FF),
    onSecondaryContainer = Color(0xFF0A1F5C),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF151A26),
    surface = Color(0xFFF4F6FB),
    onSurface = Color(0xFF151A26),
    surfaceVariant = Color(0xFFE3E7F0),
    onSurfaceVariant = Color(0xFF4F5870),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF0F7),
    surfaceContainerHighest = Color(0xFFE5E9F2),
    outline = Color(0xFFB4BCCF),
    outlineVariant = Color(0xFFDDE2EC),
    error = Color(0xFFC62828),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val Mono = FontFamily.Monospace

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = Typography()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        typography = base.copy(
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

/** Цвет аватара записи — стабильный по названию. */
private val avatarPalette = listOf(
    Color(0xFF3DDC97), Color(0xFF5B8DEF), Color(0xFFF2A65A), Color(0xFFE5677A),
    Color(0xFFA67CF0), Color(0xFF34C3D6), Color(0xFFE8C547), Color(0xFF7BC96F),
)

fun avatarColor(key: String): Color = avatarPalette[Math.floorMod(key.lowercase().hashCode(), avatarPalette.size)]

val PasswordTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 17.sp, letterSpacing = 0.5.sp)
