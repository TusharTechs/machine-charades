package com.machinecharades.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.machinecharades.data.PlayerStats
import com.machinecharades.data.Plus

/**
 * Every puzzle before today.
 *
 * The list is built from the current puzzle number and what is already stored
 * locally — no network call to draw it. Opening a row is the only fetch, which
 * matters on a screen you scroll past most of.
 */
@Composable
fun ArchiveScreen(
    currentPuzzle: Int,
    stats: PlayerStats,
    plus: Boolean,
    onPlay: (Int) -> Unit,
    onWantPlus: () -> Unit,
    onBack: () -> Unit,
) {
    // Newest first: the puzzle you just missed is the one you want.
    val numbers = (currentPuzzle - 1) downTo 1
    val unlocked = Plus.unlocked(plus)

    Column(
        Modifier.safeContentPadding().fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) { TextButton(onBack) { Text("Back") } }

        Text(
            "THE ARCHIVE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )

        when {
            numbers.isEmpty() -> Text(
                "Nothing here yet — today is the first puzzle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(numbers.toList()) { n ->
                    ArchiveRow(
                        number = n,
                        played = stats.roundFor(n),
                        locked = !unlocked,
                        onClick = { if (unlocked) onPlay(n) else onWantPlus() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveRow(
    number: Int,
    played: com.machinecharades.data.StoredRound?,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "#$number",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(52.dp),
            )
            Column(Modifier.weight(1f)) {
                when {
                    played != null -> {
                        Text(
                            if (played.solved) "It got there" else "It never got there",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (played.solved) MachineGreen else MissRed,
                        )
                        Text(
                            "${played.score} points · ${played.clue.trim().length} chars",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    locked -> Text(
                        "🔒 Plus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> Text(
                        "Not played",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
