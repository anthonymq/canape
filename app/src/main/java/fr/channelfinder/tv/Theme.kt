package fr.channelfinder.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Design tokens. Sized for a 960x540 dp TV canvas viewed from about 3 m. */
object Palette {
    val Ink = Color(0xFF0A0F18)
    val InkTop = Color(0xFF111A29)
    val Surface1 = Color(0xFF151E2C)
    val Surface2 = Color(0xFF1D2939)
    val Outline = Color(0xFF2F3D52)
    val OnSurface = Color(0xFFF2F5FA)
    val Muted = Color(0xFFBAC5D4)   // ~11:1 on Ink
    val Faint = Color(0xFF94A2B6)   // ~7:1 on Ink
    val Accent = Color(0xFF8AB8FF)
    val AccentDeep = Color(0xFF22395C)
    val AccentOnLight = Color(0xFF1C4F9C) // accent text on the light focused surface (~7:1)
    val FocusFill = Color(0xFFF2F5FA)
    val OnFocus = Color(0xFF0A0F18)
    val Gold = Color(0xFFFFCB57)
    val GoldOnLight = Color(0xFF9A6A00)
    val Live = Color(0xFFFF4D5E)
    val Warning = Color(0xFFFFCB80)
    val WarningSurface = Color(0xFF2E2414)
    val Error = Color(0xFFFF9C9C)
    val ErrorSurface = Color(0xFF331A1E)
    val LogoPlate = Color(0xFF223047)
}

object Spacing {
    val Side = 58.dp      // TV layout grid margin
    val Top = 28.dp       // 5% overscan-safe top/bottom
    val Gutter = 20.dp
    val Row = 28.dp       // between home rows
}

object Type {
    val Display = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val Headline = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val Title = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val TitleSmall = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
    val Label = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontSize = 14.sp, lineHeight = 18.sp)
    val Eyebrow = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    val Badge = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
}

@Composable
fun ChannelFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Palette.OnFocus,
            background = Palette.Ink,
            onBackground = Palette.OnSurface,
            surface = Palette.Surface1,
            onSurface = Palette.OnSurface,
            surfaceVariant = Palette.Surface2,
            onSurfaceVariant = Palette.Muted,
            inverseSurface = Palette.FocusFill,
            inverseOnSurface = Palette.OnFocus,
            border = Palette.Outline,
            error = Palette.Error
        )
    ) {
        // Light content by default on the dark background; focused surfaces override it with dark content.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.tv.material3.LocalContentColor provides Palette.OnSurface,
            content = content
        )
    }
}
