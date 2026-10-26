package id.tatap.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CrunchyrollOrange,
    onPrimary = TextPrimary,
    primaryContainer = SurfaceVariantDark,
    onPrimaryContainer = CrunchyrollOrange,
    secondary = CrunchyrollOrangeHover,
    onSecondary = TextPrimary,
    background = DarkOled,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle
)

@Composable
fun TatapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
