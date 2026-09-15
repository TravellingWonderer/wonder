package com.wonder.provider.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0D9488)
private val TealBright = Color(0xFF2DD4BF)
private val TealDeep = Color(0xFF0F766E)
private val Indigo = Color(0xFF6366F1)
private val IndigoBright = Color(0xFF818CF8)
private val Coral = Color(0xFFF97316)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5EE),
    onPrimaryContainer = TealDeep,
    secondary = Coral,
    onSecondary = Color.White,
    tertiary = Indigo,
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF12100E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12100E),
    surfaceVariant = Color(0xFFF0ECE6),
    onSurfaceVariant = Color(0xFF6F6862),
    outline = Color(0xFFDDD7CF),
    outlineVariant = Color(0xFFEBE6DF)
)

private val DarkColors = darkColorScheme(
    primary = TealBright,
    onPrimary = Color(0xFF04211F),
    primaryContainer = Color(0xFF10403C),
    onPrimaryContainer = Color(0xFFB8F5EA),
    secondary = Coral,
    onSecondary = Color(0xFF2A1206),
    tertiary = IndigoBright,
    background = Color(0xFF0A0A0C),
    onBackground = Color(0xFFF4F2EF),
    surface = Color(0xFF141417),
    onSurface = Color(0xFFF4F2EF),
    surfaceVariant = Color(0xFF1E1E23),
    onSurfaceVariant = Color(0xFF9A958F),
    outline = Color(0xFF2C2C33),
    outlineVariant = Color(0xFF222228)
)

/**
 * Colours that carry meaning beyond the Material roles — used by the ambient orb, the
 * conversation background wash and the generative cards.
 */
@Immutable
data class WonderPalette(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val aurora: List<Color>,
    val ambientWash: List<Color>,
    val userBubble: Color,
    val onUserBubble: Color,
    val cardTint: Color,
    val hairline: Color
)

private val LightPalette = WonderPalette(
    positive = Color(0xFF059669),
    negative = Color(0xFFDC2626),
    warning = Color(0xFFD97706),
    aurora = listOf(Color(0xFF2DD4BF), Color(0xFF6366F1), Color(0xFFF97316)),
    ambientWash = listOf(Color(0x2214B8A6), Color(0x1A6366F1), Color(0x00000000)),
    userBubble = Color(0xFF12100E),
    onUserBubble = Color(0xFFFAF8F5),
    cardTint = Color(0xFFFFFFFF),
    hairline = Color(0x14000000)
)

private val DarkPalette = WonderPalette(
    positive = Color(0xFF34D399),
    negative = Color(0xFFF87171),
    warning = Color(0xFFFBBF24),
    aurora = listOf(Color(0xFF2DD4BF), Color(0xFF818CF8), Color(0xFFFB923C)),
    ambientWash = listOf(Color(0x3314B8A6), Color(0x266366F1), Color(0x00000000)),
    userBubble = Color(0xFFF4F2EF),
    onUserBubble = Color(0xFF12100E),
    cardTint = Color(0xFF17171B),
    hairline = Color(0x1FFFFFFF)
)

private val LocalWonderPalette = staticCompositionLocalOf { LightPalette }

object WonderColors {
    val current: WonderPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalWonderPalette.current
}

@Composable
fun WonderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    // Screens often paint with Modifier.background instead of Surface, which never
    // sets LocalContentColor — leaving Text/Icon at the Compose default (black).
    CompositionLocalProvider(
        LocalWonderPalette provides if (darkTheme) DarkPalette else LightPalette,
        LocalContentColor provides colorScheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WonderTypography,
            content = content
        )
    }
}
