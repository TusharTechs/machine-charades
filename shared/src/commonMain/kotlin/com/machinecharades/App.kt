package com.machinecharades

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.machinecharades.core.ClueValidator
import com.machinecharades.core.ConstraintMode
import com.machinecharades.core.DailyPuzzle
import com.machinecharades.core.MAX_CLUE_CHARS
import com.machinecharades.net.ApiException
import com.machinecharades.net.GameApi
import com.machinecharades.net.redactSecrets

/** What the screen can be showing. */
private sealed interface Screen {
    data object Loading : Screen
    data class Failed(val message: String) : Screen
    data class Playing(val puzzle: DailyPuzzle) : Screen
}

@Composable
fun App(api: GameApi = remember { GameApi() }) {
    MaterialTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
        var reloads by remember { mutableIntStateOf(0) }

        LaunchedEffect(reloads) {
            screen = Screen.Loading
            screen = try {
                Screen.Playing(api.today())
            } catch (e: ApiException) {
                Screen.Failed(e.error.display)
            } catch (e: Throwable) {
                // Never render a raw platform error: NSURLError and OkHttp both
                // quote the failing URL, and the Firebase key rides in the query
                // string. Log a redacted chain, show the player something plain.
                //
                // The top-level message also tends to name only the last route
                // tried, and OkHttp hides the other failures in `suppressed` —
                // which is where a TLS error lurks behind a connect error.
                val chain = (
                    generateSequence(e) { it.cause }.take(5)
                        .joinToString(" <- ") { "${it::class.simpleName}: ${it.message}" } +
                        e.suppressedExceptions.take(4)
                            .joinToString("") { " | suppressed ${it::class.simpleName}: ${it.message}" }
                    )
                println("MC_ERROR " + redactSecrets(chain))
                Screen.Failed("Could not reach the server.")
            }
        }

        Surface(Modifier.fillMaxSize()) {
            Box(
                Modifier.safeContentPadding().fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (val s = screen) {
                    Screen.Loading -> CircularProgressIndicator()
                    is Screen.Failed -> Failure(s.message) { reloads++ }
                    is Screen.Playing -> Round(s.puzzle)
                }
            }
        }
    }
}

@Composable
private fun Failure(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onRetry) { Text("Try again") }
    }
}

/**
 * The round. Shows the word the machine has to guess, the blocked routes, and
 * takes a clue.
 *
 * The clue is validated locally as it is typed, by the same ClueValidator the
 * Worker runs server-side. That is the whole point of the shared module: instant
 * feedback with no round trip, and the server re-checks anyway because a patched
 * client cannot be trusted.
 */
@Composable
private fun Round(puzzle: DailyPuzzle) {
    var clue by remember { mutableStateOf("") }
    val rejection = remember(clue) {
        if (clue.isBlank()) null else ClueValidator.validate(clue, puzzle, ConstraintMode.NONE)
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "MACHINE CHARADES #${puzzle.number}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            puzzle.word.uppercase(),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        puzzle.category?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Blocked today", style = MaterialTheme.typography.labelLarge)
                Text(
                    puzzle.banned.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Text(
            "Write a clue. The machine gets three guesses.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        OutlinedTextField(
            value = clue,
            onValueChange = { if (it.length <= MAX_CLUE_CHARS) clue = it },
            label = { Text("Your clue") },
            isError = rejection != null,
            supportingText = {
                Text(rejection?.message() ?: "${clue.trim().length} / $MAX_CLUE_CHARS")
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Submitting is the next slice; this proves the puzzle and the local
        // validator are both live.
        Button(
            onClick = {},
            enabled = clue.isNotBlank() && rejection == null,
        ) { Text("Send to the machine") }
    }
}
