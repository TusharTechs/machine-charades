package com.machinecharades

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.machinecharades.core.ClueValidator
import com.machinecharades.core.ConstraintMode
import com.machinecharades.core.DailyPuzzle
import com.machinecharades.core.MAX_CLUE_CHARS
import com.machinecharades.core.MAX_GUESSES
import com.machinecharades.core.MachineGuess
import com.machinecharades.core.RoundResult
import com.machinecharades.core.Scoring
import com.machinecharades.net.ApiException
import com.machinecharades.net.GameApi
import com.machinecharades.net.redactSecrets
import com.machinecharades.ui.MachineCharadesTheme
import com.machinecharades.ui.MachineGreen
import com.machinecharades.ui.MissRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** What the screen can be showing. */
private sealed interface Screen {
    data object Loading : Screen
    data class Failed(val message: String) : Screen
    data class Playing(val puzzle: DailyPuzzle) : Screen
}

/** Where the round has got to. */
private sealed interface Phase {
    /** Taking a clue. */
    data object Writing : Phase

    /** Clue is in; the machine is working through its attempts. */
    data object Thinking : Phase

    /** All attempts spent or the machine got it. */
    data class Done(val result: RoundResult) : Phase
}

@Composable
fun App(api: GameApi = remember { GameApi() }) {
    MachineCharadesTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
        var reloads by remember { mutableIntStateOf(0) }

        LaunchedEffect(reloads) {
            screen = Screen.Loading
            screen = try {
                Screen.Playing(api.today())
            } catch (e: ApiException) {
                Screen.Failed(e.error.display)
            } catch (e: Throwable) {
                Screen.Failed(describe(e))
            }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                Modifier.safeContentPadding().fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (val s = screen) {
                    Screen.Loading -> CircularProgressIndicator()
                    is Screen.Failed -> Failure(s.message) { reloads++ }
                    is Screen.Playing -> Round(s.puzzle, api)
                }
            }
        }
    }
}

/**
 * Never render a raw platform error: NSURLError and OkHttp both quote the
 * failing URL, and the Firebase key rides in the query string. Log a redacted
 * chain, show the player something plain.
 *
 * The top-level message also tends to name only the last route tried, and
 * OkHttp hides the other failures in `suppressed` — which is where a TLS error
 * lurks behind a connect error.
 */
private fun describe(e: Throwable): String {
    val chain = (
        generateSequence(e) { it.cause }.take(5)
            .joinToString(" <- ") { "${it::class.simpleName}: ${it.message}" } +
            e.suppressedExceptions.take(4)
                .joinToString("") { " | suppressed ${it::class.simpleName}: ${it.message}" }
        )
    println("MC_ERROR " + redactSecrets(chain))
    return "Could not reach the server."
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
 * The round. Shows the word the machine has to guess, the blocked routes, takes
 * one clue, then plays the machine's attempts back one at a time.
 *
 * The clue is validated locally as it is typed, by the same ClueValidator the
 * Worker runs server-side. That is the whole point of the shared module:
 * instant feedback with no round trip, and the server re-checks anyway because
 * a patched client cannot be trusted.
 */
@Composable
private fun Round(puzzle: DailyPuzzle, api: GameApi) {
    var clue by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<Phase>(Phase.Writing) }
    var guesses by remember { mutableStateOf<List<MachineGuess>>(emptyList()) }
    var submitError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val started = remember { TimeSource.Monotonic.markNow() }
    val rejection = remember(clue) {
        if (clue.isBlank()) null else ClueValidator.validate(clue, puzzle, ConstraintMode.NONE)
    }

    fun send() {
        val submitted = clue.trim()
        scope.launch {
            phase = Phase.Thinking
            submitError = null
            try {
                // One clue, up to MAX_GUESSES attempts. Each is its own request
                // so the machine's thinking arrives as it happens rather than
                // as a finished list — the pause between attempts is the drama.
                for (attempt in 1..MAX_GUESSES) {
                    val reply = api.guess(puzzle.number, submitted, attempt)
                    guesses = guesses + MachineGuess(reply.guess, reply.correct, reply.confidence)
                    if (reply.correct) break
                    if (attempt < MAX_GUESSES) delay(700)
                }
                phase = Phase.Done(
                    RoundResult(
                        puzzleNumber = puzzle.number,
                        clue = submitted,
                        guesses = guesses,
                        solved = guesses.any { it.correct },
                        mode = ConstraintMode.NONE,
                        elapsedMs = started.elapsedNow().inWholeMilliseconds,
                    ),
                )
            } catch (e: ApiException) {
                submitError = e.error.display
                // Back to Writing so the clue is still there to resend. Any
                // attempts already revealed stay on screen; the retry picks up
                // from the next one rather than paying for them again.
                phase = Phase.Writing
            } catch (e: Throwable) {
                submitError = describe(e)
                phase = Phase.Writing
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
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
            color = MaterialTheme.colorScheme.onBackground,
        )
        puzzle.category?.let {
            Text(
                it.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BannedRow(puzzle.banned)

        when (val p = phase) {
            Phase.Writing -> ClueEntry(
                clue = clue,
                rejection = rejection?.message(),
                error = submitError,
                onClue = { if (it.length <= MAX_CLUE_CHARS) clue = it },
                onSend = ::send,
            )
            Phase.Thinking -> Unit
            is Phase.Done -> Unit
        }

        GuessLog(guesses, thinking = phase is Phase.Thinking)

        (phase as? Phase.Done)?.let { ResultCard(it.result) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BannedRow(banned: List<String>) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "BLOCKED TODAY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            banned.forEach { word ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        word,
                        Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClueEntry(
    clue: String,
    rejection: String?,
    error: String?,
    onClue: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Make the machine say the word.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            "The shorter your clue, the more it scores.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        OutlinedTextField(
            value = clue,
            onValueChange = onClue,
            label = { Text("Your clue") },
            isError = rejection != null,
            supportingText = {
                // The bonus, live. The raw "n / 120" this replaced was actively
                // misleading: 120 is only the hard cap, while every point of
                // brevity is already gone by 60.
                val used = clue.trim().length
                val bonus = Scoring.brevityBonus(used)
                Text(
                    text = rejection ?: when {
                        used == 0 -> "Under ${Scoring.CHAR_ALLOWANCE} characters earns a bonus."
                        bonus > 0 -> "$used chars  ·  +$bonus bonus"
                        else -> "$used chars  ·  no bonus past ${Scoring.CHAR_ALLOWANCE}"
                    },
                    color = when {
                        rejection != null -> MaterialTheme.colorScheme.error
                        bonus > 0 -> MachineGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Button(
            onClick = onSend,
            enabled = clue.isNotBlank() && rejection == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Send to the machine") }
    }
}

/** The machine's attempts, in the order they landed. */
@Composable
private fun GuessLog(guesses: List<MachineGuess>, thinking: Boolean) {
    if (guesses.isEmpty() && !thinking) return

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        guesses.forEachIndexed { i, g ->
            AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                GuessRow(index = i + 1, guess = g)
            }
        }
        if (thinking && guesses.size < MAX_GUESSES) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuessRow(index: Int, guess: MachineGuess) {
    val tint = if (guess.correct) MachineGreen else MissRed
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (guess.correct) "🟩" else "🟥",
                fontSize = 18.sp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    guess.guess.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                )
                Text(
                    "guess $index of $MAX_GUESSES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: RoundResult) {
    val share = rememberShareAction()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (result.solved) "It got there." else "It never got there.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (result.solved) MachineGreen else MissRed,
            )

            Text(
                if (result.solved) {
                    val g = result.guessesUsed
                    "${g} ${if (g == 1) "guess" else "guesses"} · ${result.clueChars} chars"
                } else {
                    "Three guesses, no luck. Zero points — the machine has to understand you."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Text(
                "${result.score}",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "POINTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { share(Scoring.shareString(result)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Share result") }

            Text(
                "Come back tomorrow for #${result.puzzleNumber + 1}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
