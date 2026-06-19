package com.tingmusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 红黑设计 token(集中,供所有 UI 引用,不走 MaterialTheme 也能直接用)。 */
object RB {
    val Bg = Color(0xFF0A0A0A)
    val MiniBg = Color(0xFF0D0D0D)
    val DrawerBg = Color(0xFF0F0F0F)
    val SearchBg = Color(0xFF161616)
    val Text = Color(0xFFF5F5F5)
    val TextDim = Color(0x66FFFFFF)   // ~40%
    val TextWeak = Color(0x47FFFFFF)  // ~28%
    val Red = Color(0xFFE60026)
    val Divider = Color(0x0FFFFFFF)   // ~6%
}

private val RedBlackScheme = darkColorScheme(
    primary = RB.Red,
    onPrimary = Color.White,
    background = RB.Bg,
    surface = RB.MiniBg,
    onBackground = RB.Text,
    onSurface = RB.Text,
    onSurfaceVariant = RB.TextDim,
)

@Composable
fun TingMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RedBlackScheme, content = content)
}
