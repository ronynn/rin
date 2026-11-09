// app/src/main/java/io/github/ronynn/rin/RinTheme.kt
package io.github.ronynn.rin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class RinColors(
    val bg: Color, val fg: Color, val accent: Color, val border: Color,
    val panel: Color, val listHover: Color
)

val Themes: List<Pair<String, RinColors>> = listOf(
    "default" to RinColors(Color(0xFF111111), Color(0xFFFFFFFF), Color(0xFF888888), Color(0xFFFFFFFF), Color(0xFF111111), Color(0xFF333333)),
    "gruvbox" to RinColors(Color(0xFF282828), Color(0xFFEBDBB2), Color(0xFFD79921), Color(0xFFA89984), Color(0xFF1D2021), Color(0xFF3C3836)),
    "catppuccin" to RinColors(Color(0xFF1E1E2E), Color(0xFFCDD6F4), Color(0xFFCBA6F7), Color(0xFF585B70), Color(0xFF181825), Color(0xFF313244)),
    "nord" to RinColors(Color(0xFF2E3440), Color(0xFFECEFF4), Color(0xFF88C0D0), Color(0xFF4C566A), Color(0xFF242933), Color(0xFF3B4252)),
    "dracula" to RinColors(Color(0xFF282A36), Color(0xFFF8F8F2), Color(0xFFBD93F9), Color(0xFF6272A4), Color(0xFF21222C), Color(0xFF44475A)),
    "tokyo" to RinColors(Color(0xFF1A1B26), Color(0xFFC0CAF5), Color(0xFF7AA2F7), Color(0xFF414868), Color(0xFF16161E), Color(0xFF292E42)),
    "light" to RinColors(Color(0xFFFFFFFF), Color(0xFF111111), Color(0xFF0066CC), Color(0xFF111111), Color(0xFFF5F5F5), Color(0xFFE0E0E0)),
    "solar" to RinColors(Color(0xFFFDF6E3), Color(0xFF073642), Color(0xFFCB4B16), Color(0xFF93A1A1), Color(0xFFEEE8D5), Color(0xFFE0DCC7))
)

val LocalRinColors = staticCompositionLocalOf { Themes[0].second }

@Composable
fun RinTheme(index: Int, content: @Composable () -> Unit) {
    val colors = Themes.getOrNull(index)?.second ?: Themes[0].second
    CompositionLocalProvider(LocalRinColors provides colors, content = content)
}