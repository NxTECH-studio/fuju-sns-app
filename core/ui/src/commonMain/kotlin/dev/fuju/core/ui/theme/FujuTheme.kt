package dev.fuju.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Fuju デザイントークンを Material3 のテーマに載せるエントリポイント。
 * アプリ側（`:composeApp` 以下）は常に `FujuTheme { ... }` で包む。
 */
@Composable
fun FujuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkFujuColorScheme else LightFujuColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = fujuTypography(),
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

private fun fujuTypography(): Typography =
    Typography(
        displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
        displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
        headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
        titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    )
