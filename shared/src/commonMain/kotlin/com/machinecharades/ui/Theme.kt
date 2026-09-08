package com.machinecharades.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import machinecharades.shared.generated.resources.Res
import machinecharades.shared.generated.resources.jetbrains_mono
import org.jetbrains.compose.resources.Font

/**
 * One fixed dark theme, deliberately not following the system.
 *
 * The round is a reveal: red squares landing one at a time, then green or not.
 * Those colours have to read the same for everyone, and on a light background
 * the misses turn into a wall of pink. A game may pick its own look.
 */

/** The machine's colour. Used for its correct guess and for anything it says. */
val MachineGreen = Color(0xFF4ADE80)

/** A miss. Also the validator's rejection state on the clue field. */
val MissRed = Color(0xFFF87171)

private val Scheme = darkColorScheme(
    primary = MachineGreen,
    onPrimary = Color(0xFF04140A),
    secondary = Color(0xFF7DD3FC),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF161A21),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF1F242D),
    onSurfaceVariant = Color(0xFF9AA3AF),
    error = MissRed,
    onError = Color(0xFF1A0505),
    outline = Color(0xFF2C333E),
)

/**
 * The machine's voice.
 *
 * The same face the landing page uses for its labels, now bundled so the app
 * stops looking like whatever the OS happened to ship — SF Pro on one
 * platform, Roboto on the other, neither of them the brand. A word game about
 * making a machine understand you has some business being set in a machine's
 * typeface.
 *
 * Prose stays on the system font, which is what the site does too: monospace
 * is superb for a word, a counter or a label and tiring for a paragraph.
 */
@Composable
private fun machineFont() = FontFamily(Font(Res.font.jetbrains_mono))

private fun typographyWith(mono: FontFamily) = Typography().let { base ->
    base.copy(
        // The secret word is the hero of the screen; everything else is chrome.
        displayMedium = base.displayMedium.copy(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        ),
        // Every label, eyebrow and counter: BLOCKED TODAY, MACHINE CHARADES #6,
        // IT HEARD, the guess numbers. These are the machine's own annotations
        // on the screen, and they read as such in mono.
        labelSmall = base.labelSmall.copy(fontFamily = mono, letterSpacing = 1.4.sp),
        labelMedium = base.labelMedium.copy(fontFamily = mono, letterSpacing = 1.2.sp),
        labelLarge = base.labelLarge.copy(fontFamily = mono, letterSpacing = 1.2.sp),
        // The guessed word and the score: things the machine produced.
        titleMedium = base.titleMedium.copy(fontFamily = mono, letterSpacing = 0.5.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = mono, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun MachineCharadesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = typographyWith(machineFont()),
        content = content,
    )
}

/** Puzzle numbers and counters. Actually monospaced now, rather than feeling it. */
val CounterStyle = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
