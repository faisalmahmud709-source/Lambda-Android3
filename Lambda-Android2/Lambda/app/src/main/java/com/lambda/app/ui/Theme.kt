package com.lambda.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class LambdaPalette(
    val id: String, val label: String, val isLight: Boolean,
    val bg: Color, val sidebar: Color, val card: Color, val elevated: Color, val field: Color,
    val primary: Color, val onPrimary: Color, val text: Color, val muted: Color, val purple: Color,
    val red: Color, val emerald: Color, val amber: Color, val indigo: Color, val blue: Color
)

object LambdaPalettes {
    val all = listOf(
        LambdaPalette("light", "Light", true, Color(0xFFF5F6FA), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFE9ECF4), Color(0xFFF0F2F7), Color(0xFF5B4BDB), Color.White, Color(0xFF171923), Color(0xFF626879), Color(0xFF7657D5), Color(0xFFB42318), Color(0xFF16845B), Color(0xFFB7791F), Color(0xFF4F46A5), Color(0xFF2563EB)),
        LambdaPalette("dark", "Dark", false, Color(0xFF17191F), Color(0xFF111318), Color(0xFF20232B), Color(0xFF2B2F39), Color(0xFF242832), Color(0xFF9CA3AF), Color(0xFF17191F), Color(0xFFF4F5F7), Color(0xFFA6ADBB), Color(0xFFA78BFA), Color(0xFFF87171), Color(0xFF34D399), Color(0xFFFCD34D), Color(0xFF818CF8), Color(0xFF60A5FA)),
        LambdaPalette("night", "Night", false, Color(0xFF0B1020), Color(0xFF080C18), Color(0xFF121A30), Color(0xFF1A2440), Color(0xFF151E36), Color(0xFF7C9CFF), Color(0xFF0B1020), Color(0xFFE6EBFA), Color(0xFF8A95B5), Color(0xFF8B7CF6), Color(0xFFF87171), Color(0xFF34D399), Color(0xFFFCD34D), Color(0xFF818CF8), Color(0xFF60A5FA)),
        LambdaPalette("midnight", "Cyber Midnight", false, Color(0xFF0C0818), Color(0xFF090614), Color(0xFF150F28), Color(0xFF1D1538), Color(0xFF141417), Color(0xFFF0BC68), Color(0xFF181308), Color(0xFFF4F4F7), Color(0xFF9B98AB), Color(0xFFA855F7), Color(0xFFF87171), Color(0xFF34D399), Color(0xFFFCD34D), Color(0xFF818CF8), Color(0xFF60A5FA)),
        LambdaPalette("ocean", "Ocean", false, Color(0xFF071827), Color(0xFF04111D), Color(0xFF0C263A), Color(0xFF12364F), Color(0xFF102B40), Color(0xFF38BDF8), Color(0xFF062033), Color(0xFFE8F5FF), Color(0xFF91AFC3), Color(0xFF2DD4BF), Color(0xFFFB7185), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFF818CF8), Color(0xFF60A5FA)),
        LambdaPalette("forest", "Forest", false, Color(0xFF101B16), Color(0xFF0A130F), Color(0xFF192820), Color(0xFF24392D), Color(0xFF1B2D23), Color(0xFF86C58A), Color(0xFF102016), Color(0xFFEAF4EC), Color(0xFFA0B5A4), Color(0xFF6DBB9A), Color(0xFFF87171), Color(0xFF6EE7B7), Color(0xFFFCD34D), Color(0xFFA5B4FC), Color(0xFF7DD3FC)),
        LambdaPalette("rose", "Rose", false, Color(0xFF21131C), Color(0xFF170D14), Color(0xFF2D1B28), Color(0xFF3B2534), Color(0xFF321F2C), Color(0xFFF0A6C5), Color(0xFF2A1421), Color(0xFFFFF0F6), Color(0xFFC6A7B7), Color(0xFFC084FC), Color(0xFFFB7185), Color(0xFF6EE7B7), Color(0xFFFCD34D), Color(0xFFA5B4FC), Color(0xFF7DD3FC)),
        LambdaPalette("amoled", "AMOLED Black", false, Color.Black, Color.Black, Color(0xFF080808), Color(0xFF151515), Color(0xFF101010), Color(0xFFB8A1FF), Color(0xFF160D2A), Color.White, Color(0xFF9CA3AF), Color(0xFFB8A1FF), Color(0xFFF87171), Color(0xFF34D399), Color(0xFFFCD34D), Color(0xFF818CF8), Color(0xFF60A5FA))
    )
    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first { it.id == "midnight" }
}

object LC {
    private var palette by mutableStateOf(LambdaPalettes.get("midnight"))
    fun setTheme(id: String) { val next = LambdaPalettes.get(id); if (palette.id != next.id) palette = next }
    val Bg get() = palette.bg
    val Sidebar get() = palette.sidebar
    val Card get() = palette.card
    val Elevated get() = palette.elevated
    val Field get() = palette.field
    val Primary get() = palette.primary
    val OnPrimary get() = palette.onPrimary
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Purple get() = palette.purple
    val Border get() = palette.purple.copy(alpha = 0.15f)
    val BorderStrong get() = palette.purple.copy(alpha = 0.32f)
    val White05 get() = (if (palette.isLight) Color.Black else Color.White).copy(alpha = 0.05f)
    val White10 get() = (if (palette.isLight) Color.Black else Color.White).copy(alpha = 0.10f)
    val Red get() = palette.red
    val Emerald get() = palette.emerald
    val Amber get() = palette.amber
    val Indigo get() = palette.indigo
    val Blue get() = palette.blue
    /** Soft background glow; pure black themes keep a truly black background. */
    val Glow get() = if (palette.id == "amoled") Color.Transparent else palette.purple.copy(alpha = 0.08f)
    val CardBrush get() = Brush.verticalGradient(listOf(palette.card, palette.bg))
    val ChartColors get() = listOf(palette.primary, palette.blue, palette.emerald, palette.purple, palette.red, palette.indigo, palette.amber)
}

val DisplayFont = FontFamily.Serif
val MonoFont = FontFamily.Monospace

private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, color = LC.Text),
    bodyMedium = TextStyle(fontSize = 13.sp, color = LC.Text),
    bodySmall = TextStyle(fontSize = 12.sp, color = LC.Muted),
    titleLarge = TextStyle(fontFamily = DisplayFont, fontStyle = FontStyle.Italic, fontSize = 28.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = 1.4.sp)
)

@Composable
fun LambdaTheme(themeId: String = "midnight", content: @Composable () -> Unit) {
    SideEffect { LC.setTheme(themeId) }
    val p = LambdaPalettes.get(themeId)
    val scheme = if (p.isLight) lightColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, secondary = p.purple,
        background = p.bg, onBackground = p.text, surface = p.card, onSurface = p.text,
        surfaceVariant = p.elevated, onSurfaceVariant = p.muted, outline = p.purple.copy(alpha = 0.4f),
        error = p.red
    ) else darkColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, secondary = p.purple,
        background = p.bg, onBackground = p.text, surface = p.card, onSurface = p.text,
        surfaceVariant = p.elevated, onSurfaceVariant = p.muted, outline = p.purple.copy(alpha = 0.4f),
        error = p.red
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
