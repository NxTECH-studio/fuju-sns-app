package dev.fuju.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fuju ブランドのパレット。`../frontend/src/index.css` の CSS custom properties と
 * `../auth-component/` の default.css から抽出したトークンをここに集約。
 *
 * - Primary: 藤色 (wisteria)。アクセントとして日本語サービス名に合わせ紫系統
 * - Secondary: Sakura pink
 * - Surface / background はダークモード優先（SNS は夜間利用が多い前提）
 */
object FujuColors {
    // Brand
    val Wisteria50 = Color(0xFFF5F2FC)
    val Wisteria100 = Color(0xFFE4D9F7)
    val Wisteria300 = Color(0xFFB193E3)
    val Wisteria500 = Color(0xFF8A63D2)
    val Wisteria700 = Color(0xFF6E45B0)
    val Wisteria900 = Color(0xFF3F1D75)

    val Sakura100 = Color(0xFFFFE4EC)
    val Sakura500 = Color(0xFFFF7FA0)
    val Sakura700 = Color(0xFFD84B76)

    // Neutral
    val Ink50 = Color(0xFFF9FAFB)
    val Ink100 = Color(0xFFF1F3F5)
    val Ink300 = Color(0xFFCED4DA)
    val Ink500 = Color(0xFF868E96)
    val Ink700 = Color(0xFF495057)
    val Ink900 = Color(0xFF212529)

    // Status
    val Positive500 = Color(0xFF2F9E44)
    val Warn500 = Color(0xFFE67700)
    val Danger500 = Color(0xFFD9480F)
}

val LightFujuColorScheme: ColorScheme = lightColorScheme(
    primary = FujuColors.Wisteria700,
    onPrimary = Color.White,
    primaryContainer = FujuColors.Wisteria100,
    onPrimaryContainer = FujuColors.Wisteria900,
    secondary = FujuColors.Sakura500,
    onSecondary = Color.White,
    secondaryContainer = FujuColors.Sakura100,
    onSecondaryContainer = FujuColors.Sakura700,
    background = FujuColors.Ink50,
    onBackground = FujuColors.Ink900,
    surface = Color.White,
    onSurface = FujuColors.Ink900,
    surfaceVariant = FujuColors.Ink100,
    onSurfaceVariant = FujuColors.Ink700,
    outline = FujuColors.Ink300,
    error = FujuColors.Danger500,
    onError = Color.White,
)

val DarkFujuColorScheme: ColorScheme = darkColorScheme(
    primary = FujuColors.Wisteria300,
    onPrimary = FujuColors.Wisteria900,
    primaryContainer = FujuColors.Wisteria900,
    onPrimaryContainer = FujuColors.Wisteria100,
    secondary = FujuColors.Sakura500,
    onSecondary = FujuColors.Ink900,
    secondaryContainer = FujuColors.Sakura700,
    onSecondaryContainer = FujuColors.Sakura100,
    background = Color(0xFF121212),
    onBackground = FujuColors.Ink50,
    surface = Color(0xFF181A1F),
    onSurface = FujuColors.Ink50,
    surfaceVariant = Color(0xFF22252C),
    onSurfaceVariant = FujuColors.Ink300,
    outline = FujuColors.Ink500,
    error = FujuColors.Danger500,
    onError = Color.White,
)
