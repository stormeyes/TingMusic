package com.tingmusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppTheme {
    DEFAULT,    // 中性灰,红仅作锚点
    WHITE_RED;  // 白底 + 红强调(网易云外链风)

    companion object {
        fun fromStored(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private val Red = Color(0xFFD33A31)

// 默认:暗灰中性,主色用红作锚点
private val DefaultColors = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Color(0xFF242424),
    surface = Color(0xFF2C2C2C),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFFAFAFAF),
)

// 白红:白底,红强调
private val WhiteRedColors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF8A8A8A),
)

@Composable
fun TingMusicTheme(theme: AppTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (theme == AppTheme.WHITE_RED) WhiteRedColors else DefaultColors,
        content = content,
    )
}
