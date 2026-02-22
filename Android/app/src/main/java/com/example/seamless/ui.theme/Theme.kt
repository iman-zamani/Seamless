package com.example.seamless.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = BgColor,
    surface = FrameColor,
    primary = PrimaryPurple,
    onPrimary = TextColor,
    onBackground = TextColor,
    onSurface = TextColor
)

@Composable
fun SeamlessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}