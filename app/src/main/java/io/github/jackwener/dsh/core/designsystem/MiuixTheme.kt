package io.github.jackwener.dsh.core.designsystem

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * Miuix pilot scope: wraps a screen (or a screen section) in [MiuixTheme] so Miuix components
 * render with the HyperOS palette, while the surrounding Dsh theme stays in charge of everything
 * else. The light/dark choice follows the Dsh theme's resolved dark flag ([LocalDshDark]), so a
 * pilot page never disagrees with the rest of the app about light vs dark.
 */
@Composable
fun DshMiuixTheme(content: @Composable () -> Unit) {
    val dark = LocalDshDark.current
    MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme(), content = content)
}
