package com.machinecharades.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

private val Type = Typography().let { base ->
    base.copy(
        // The secret word is the hero of the screen; everything else is chrome.
        displayMedium = base.displayMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        ),
        labelLarge = base.labelLarge.copy(letterSpacing = 1.2.sp),
    )
}

@Composable
fun MachineCharadesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}

/** Monospaced-feeling label for puzzle numbers and counters. */
val CounterStyle = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
