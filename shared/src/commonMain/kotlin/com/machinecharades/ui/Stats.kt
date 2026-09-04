package com.machinecharades.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.machinecharades.data.PlayerStats

/**
 * The record. Everything here was already being stored and never shown.
 *
 * Deliberately no charts: with a handful of rounds a chart is decoration, and
 * the numbers that matter — the run you are on, and how short you can get —
 * read better as numbers.
 */
@Composable
fun StatsScreen(stats: PlayerStats, plus: Boolean, onWantPlus: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.safeContentPadding().fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth()) { TextButton(onBack) { Text("Back") } }

        Text(
            "YOUR RECORD",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Figure(stats.currentStreak.toString(), "streak")
            Figure(stats.maxStreak.toString(), "best")
            Figure(stats.played.toString(), "played")
        }

        if (plus) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Figure("${stats.solveRate}%", "understood")
                Figure(stats.shortestClue?.toString() ?: "—", "shortest")
                Figure(stats.totalScore.toString(), "points")
            }

            stats.averageClueChars?.let { avg ->
                Text(
                    "Your clues average $avg characters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Solve rate, shortest clue, points and your clue-length average are part of Plus.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onWantPlus) { Text("See Plus") }
                }
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
