package fr.channelfinder.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Design tokens. Sized for a 960x540 dp TV canvas viewed from about 3 m. */
object Palette {
    val Ink = Color(0xFF171715)
    val InkTop = Color(0xFF24231F)
    val Surface1 = Color(0xFF242420)
    val Surface2 = Color(0xFF30302A)
    val Outline = Color(0xFF49483F)
    val OnSurface = Color(0xFFF4F0E8)
    val Muted = Color(0xFFCCC7BA)
    val Faint = Color(0xFFADA99D)
    val Accent = Color(0xFFD6B477)
    val AccentDeep = Color(0xFF3D3528)
    val AccentOnLight = Color(0xFF705022)
    val FocusFill = Color(0xFFF4F0E8)
    val OnFocus = Color(0xFF171715)
    val Gold = Accent
    val GoldOnLight = AccentOnLight
    val Live = Color(0xFFFF4D5E)
    val Warning = Color(0xFFFFCB80)
    val WarningSurface = Color(0xFF2E2414)
    val Error = Color(0xFFFF9C9C)
    val ErrorOnLight = Color(0xFFA52332)
    val ErrorSurface = Color(0xFF331A1E)
    val LogoPlate = Color(0xFF34342D)
}

object Spacing {
    val RailWidth = 44.dp
    val RailExpanded = 240.dp
    val RailStart = 12.dp
    val RailInset = 16.dp
    val Side = 58.dp      // TV layout grid margin
    val Top = 28.dp       // 5% overscan-safe top/bottom
    val Gutter = 12.dp
    val Row = 16.dp       // between home rows
    val ListGap = 4.dp
    val ListPadding = 4.dp
    val ChannelArtwork = 54.dp
    val ContentTop = Top
}

object NavigationLayout {
    val RowHeight = 42.dp
    val IconSize = 24.dp
    val PillRadius = 21.dp
    val GroupGap = 8.dp
    val ItemInset = 10.dp
}

object UpdateLayout {
    val DialogRadius = 16.dp
    val ActionGap = 8.dp
    val ProgressHeight = 4.dp
}

object Cinema {
    val FeatureHeight = 112.dp
    val LargeFeatureHeight = 128.dp
    const val ArtworkSettleMs = 250L
    val Background = Palette.Ink
    val Panel = Palette.Surface1
    val Accent = Palette.Accent
    val CardRadius = 8.dp
    val HeroHeight = 128.dp
    val GridGap = 10.dp
    val ResumeWidth = 200.dp
    const val PosterRatio = 2f / 3f
    const val LargePosterRatio = 0.95f
}

object Type {
    val Salon = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium)
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
