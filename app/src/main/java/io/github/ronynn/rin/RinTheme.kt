package io.github.ronynn.rin

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

data class RadialBlob(val color: Color, val cx: Float, val cy: Float, val r: Float)

data class RinColors(
    val bg: Color, val fg: Color, val accent: Color, val dim: Color,
    val panel: Color, val listHover: Color, val border: Color,
    val isLight: Boolean = false, val isGlass: Boolean = false,
    val blobs: List<RadialBlob> = emptyList()
)

data class RinFont(val family: FontFamily, val weight: FontWeight)

val Themes: List<Pair<String, RinColors>> = listOf(
    "default" to RinColors(Color(0xFF111111), Color(0xFFFFFFFF), Color(0xFF888888), Color(0xFF555555), Color(0xFF111111), Color(0xFF333333), Color(0xFFFFFFFF)),
    "gruvbox" to RinColors(Color(0xFF282828), Color(0xFFEBDBB2), Color(0xFFD79921), Color(0xFF7C6F64), Color(0xFF1D2021), Color(0xFF3C3836), Color(0xFFEBDBB2)),
    "nord" to RinColors(Color(0xFF2E3440), Color(0xFFECEFF4), Color(0xFF88C0D0), Color(0xFF616E88), Color(0xFF242933), Color(0xFF3B4252), Color(0xFFECEFF4)),
    "dracula" to RinColors(Color(0xFF282A36), Color(0xFFF8F8F2), Color(0xFFBD93F9), Color(0xFF6272A4), Color(0xFF21222C), Color(0xFF44475A), Color(0xFFF8F8F2)),
    "tokyo" to RinColors(Color(0xFF1A1B26), Color(0xFFC0CAF5), Color(0xFF7AA2F7), Color(0xFF565F89), Color(0xFF16161E), Color(0xFF292E42), Color(0xFFC0CAF5)),
    "light" to RinColors(Color(0xFFFFFFFF), Color(0xFF111111), Color(0xFF0066CC), Color(0xFF999999), Color(0xFFF5F5F5), Color(0xFFE0E0E0), Color(0xFF111111), isLight = true),
    "solar" to RinColors(Color(0xFFFDF6E3), Color(0xFF073642), Color(0xFFCB4B16), Color(0xFF93A1A1), Color(0xFFEEE8D5), Color(0xFFE0DCC7), Color(0xFF073642), isLight = true),
    "dynamic" to RinColors(Color(0xFF0C0C12), Color(0xFFE0E0F0), Color(0xFF6FB8FF), Color(0xFF55557A), Color(0xFF101018), Color(0xFF2A2A3A), Color(0xFFE0E0F0)),
    "winamp" to RinColors(Color(0xFFC0C0C0), Color(0xFF000000), Color(0xFF000080), Color(0xFF808080), Color(0xFFC0C0C0), Color(0xFFA8A8A8), Color(0xFF808080), isLight = true),
    "monokai" to RinColors(Color(0xFF272822), Color(0xFFF8F8F2), Color(0xFFF92672), Color(0xFF75715E), Color(0xFF1E1F1C), Color(0xFF3E3D32), Color(0xFFF8F8F2)),
    "crt-amber" to RinColors(Color(0xFF1A1000), Color(0xFFFFB000), Color(0xFFFFCC33), Color(0xFF806000), Color(0xFF100A00), Color(0xFF2A1C00), Color(0xFFFFB000)),
    "forest" to RinColors(Color(0xFF1E2A1E), Color(0xFFD4E7C5), Color(0xFFA8C98C), Color(0xFF6D8A6D), Color(0xFF162016), Color(0xFF2A3A2A), Color(0xFFD4E7C5)),
    "ev-light" to RinColors(Color(0xFFFDF6E3), Color(0xFF5C6A72), Color(0xFF8DA101), Color(0xFF939F91), Color(0xFFF4F0D9), Color(0xFFE6E2CC), Color(0xFF5C6A72), isLight = true),
    "chrome" to RinColors(Color(0xFF2A2A2A), Color(0xFFE0E0E0), Color(0xFFFF6600), Color(0xFF666666), Color(0xFF333333), Color(0xFF444444), Color(0xFF555555)),
    "glass-light" to RinColors(
        bg = Color(0xFFE8DFFF), fg = Color(0xFF1A1A2E),
        accent = Color(0xFFD946EF), dim = Color(0xFF55557A),
        panel = Color(0x80FFFFFF), listHover = Color(0x66FFFFFF),
        border = Color(0xD9FFFFFF), isLight = true, isGlass = true,
        blobs = listOf(
            RadialBlob(Color(0xE6FF6B9D), 0.12f, 0.12f, 1.30f),
            RadialBlob(Color(0xD960A5FA), 0.88f, 0.22f, 1.25f),
            RadialBlob(Color(0xD9FBBF24), 0.50f, 0.95f, 1.25f)
        )
    ),
    "glass-moss" to RinColors(
        bg = Color(0xFFE0F2E0), fg = Color(0xFF1A2E1A),
        accent = Color(0xFF4CAF50), dim = Color(0xFF557A55),
        panel = Color(0x80FFFFFF), listHover = Color(0x66FFFFFF),
        border = Color(0xD9FFFFFF), isLight = true, isGlass = true,
        blobs = listOf(
            RadialBlob(Color(0xD94CAF50), 0.12f, 0.12f, 1.30f),
            RadialBlob(Color(0xD92E7D32), 0.88f, 0.22f, 1.25f),
            RadialBlob(Color(0xD9AED581), 0.50f, 0.95f, 1.25f)
        )
    ),
    "glass-sunset" to RinColors(
        bg = Color(0xFFFFE0D0), fg = Color(0xFF2E1A1A),
        accent = Color(0xFFFF6B6B), dim = Color(0xFF7A5555),
        panel = Color(0x80FFFFFF), listHover = Color(0x66FFFFFF),
        border = Color(0xD9FFFFFF), isLight = true, isGlass = true,
        blobs = listOf(
            RadialBlob(Color(0xE6FF6B6B), 0.12f, 0.12f, 1.30f),
            RadialBlob(Color(0xD9FFB74D), 0.88f, 0.22f, 1.25f),
            RadialBlob(Color(0xD9EF5350), 0.50f, 0.95f, 1.25f)
        )
    )
)

val Fonts: List<Pair<String, RinFont>> = listOf(
    "sans" to RinFont(FontFamily.SansSerif, FontWeight.Normal),
    "sans-light" to RinFont(FontFamily.SansSerif, FontWeight.Light),
    "condensed" to RinFont(FontFamily.SansSerif, FontWeight.Normal),
    "serif" to RinFont(FontFamily.Serif, FontWeight.Normal),
    "mono" to RinFont(FontFamily.Monospace, FontWeight.Normal)
)

val LocalRinColors = staticCompositionLocalOf { Themes[0].second }
val LocalRinFont = staticCompositionLocalOf { Fonts[0].second }

@Composable
fun RinTheme(
    themeIndex: Int,
    fontIndex: Int,
    dynamicColors: RinColors? = null,
    content: @Composable () -> Unit
) {
    val colors: RinColors = when {
        themeIndex == 7 -> dynamicColors ?: Themes[7].second
        else -> Themes.getOrNull(themeIndex)?.second ?: Themes[0].second
    }
    val font = Fonts.getOrNull(fontIndex)?.second ?: Fonts[0].second
    CompositionLocalProvider(
        LocalRinColors provides colors,
        LocalRinFont provides font,
        LocalTextStyle provides TextStyle(
            fontFamily = font.family,
            fontWeight = font.weight,
            color = colors.fg
        ),
        content = content
    )
}