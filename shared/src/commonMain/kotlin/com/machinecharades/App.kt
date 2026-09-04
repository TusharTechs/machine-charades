package com.machinecharades

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.clickable
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
import com.machinecharades.data.Plan
import com.machinecharades.data.PlayerStats
import com.machinecharades.data.PlayerStore
import com.machinecharades.data.Plus
import com.machinecharades.data.asResult
import com.machinecharades.net.ApiException
import com.machinecharades.net.GameApi
import com.machinecharades.net.redactSecrets
import com.machinecharades.ui.MachineCharadesTheme
import com.machinecharades.ui.ArchiveScreen
import com.machinecharades.ui.Paywall
import com.machinecharades.ui.StatsScreen
import com.machinecharades.ui.MachineGreen
import com.machinecharades.ui.MissRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** What the screen can be showing. */
private sealed interface Screen {
    data object Loading : Screen
    data class Failed(val message: String) : Screen

    /** [fromArchive] rounds are replays of a past day, so they offer a way back. */
    data class Playing(val puzzle: DailyPuzzle, val fromArchive: Boolean = false) : Screen
    data object Archive : Screen
    data object Stats : Screen
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
fun App(
    api: GameApi = remember { GameApi() },
    store: PlayerStore = remember { PlayerStore() },
) {
    MachineCharadesTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
        var stats by remember { mutableStateOf(PlayerStats()) }
        var reloads by remember { mutableIntStateOf(0) }

        var plus by remember { mutableStateOf(false) }
        var paywallOpen by remember { mutableStateOf(false) }
        var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
        var busy by remember { mutableStateOf(false) }
        var buyError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            Plus.start()
            plus = Plus.isActive()
        }

        // Offerings are fetched when the paywall first opens, not at launch —
        // most players never see it, and it is a network call.
        LaunchedEffect(paywallOpen) {
            if (paywallOpen && plans.isEmpty()) {
                busy = true
                plans = Plus.plans()
                busy = false
            }
        }

        // Today's puzzle, kept so the archive knows where the past ends and so
        // leaving a replay does not need a second fetch.
        var today by remember { mutableStateOf<DailyPuzzle?>(null) }

        LaunchedEffect(reloads) {
            screen = Screen.Loading
            // Read before the network call: a returning player should see their
            // streak on the same frame as the puzzle, not a beat later.
            stats = store.load()
            screen = try {
                val puzzle = api.today()
                today = puzzle
                Screen.Playing(puzzle)
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
                    is Screen.Playing -> Round(
                        puzzle = s.puzzle,
                        api = api,
                        stats = stats,
                        plus = plus,
                        onWantPlus = { paywallOpen = true },
                        onArchive = { screen = Screen.Archive },
                        onStats = { screen = Screen.Stats },
                        onBack = if (s.fromArchive) {
                            { screen = Screen.Archive }
                        } else {
                            null
                        },
                    ) { finished ->
                        stats = stats.recording(finished).also(store::save)
                    }

                    Screen.Archive -> ArchiveScreen(
                        currentPuzzle = today?.number ?: 1,
                        stats = stats,
                        plus = plus,
                        onPlay = { n ->
                            scope.launch {
                                screen = Screen.Loading
                                screen = try {
                                    Screen.Playing(api.archived(n), fromArchive = true)
                                } catch (e: ApiException) {
                                    Screen.Failed(e.error.display)
                                } catch (e: Throwable) {
                                    Screen.Failed(describe(e))
                                }
                            }
                        },
                        onWantPlus = { paywallOpen = true },
                        onBack = { today?.let { screen = Screen.Playing(it) } },
                    )

                    Screen.Stats -> StatsScreen(
                        stats = stats,
                        plus = plus,
                        onWantPlus = { paywallOpen = true },
                        onBack = { today?.let { screen = Screen.Playing(it) } },
                    )
                }

                if (paywallOpen) {
                    Paywall(
                        plans = plans,
                        busy = busy,
                        error = buyError,
                        onBuy = { plan ->
                            scope.launch {
                                busy = true; buyError = null
                                val bought = Plus.buy(plan.id)
                                busy = false
                                if (bought) { plus = true; paywallOpen = false }
                                // A cancelled purchase lands here too, which is
                                // why this says nothing about what went wrong —
                                // most of the time nothing did.
                                else buyError = "That didn't go through. You have not been charged."
                            }
                        },
                        onRestore = {
                            scope.launch {
                                busy = true; buyError = null
                                val restored = Plus.restore()
                                busy = false
                                if (restored) { plus = true; paywallOpen = false }
                                else buyError = "No purchase found on this account."
                            }
                        },
                        onDismiss = { paywallOpen = false; buyError = null },
                    )
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
private fun Round(
    puzzle: DailyPuzzle,
    api: GameApi,
    stats: PlayerStats,
    plus: Boolean,
    onWantPlus: () -> Unit,
    onArchive: () -> Unit,
    onStats: () -> Unit,
    /** Non-null only for a replay, which is the one round you can leave. */
    onBack: (() -> Unit)?,
    onFinished: (RoundResult) -> Unit,
) {
    // Today may already be behind us. Reopening the app has to show what you
    // scored, not hand you a second attempt at a puzzle you have played.
    val alreadyPlayed = remember(puzzle.number) { stats.roundFor(puzzle.number) }

    var clue by remember { mutableStateOf(alreadyPlayed?.clue ?: "") }
    var phase by remember {
        mutableStateOf<Phase>(
            alreadyPlayed?.let { Phase.Done(it.asResult()) } ?: Phase.Writing,
        )
    }
    var guesses by remember { mutableStateOf(alreadyPlayed?.guesses ?: emptyList()) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(ConstraintMode.NONE) }

    val scope = rememberCoroutineScope()
    val started = remember { TimeSource.Monotonic.markNow() }
    val rejection = remember(clue, mode) {
        if (clue.isBlank()) null else ClueValidator.validate(clue, puzzle, mode)
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
                var par: Int? = null
                var best: Int? = null
                var solvers = 0
                for (attempt in 1..MAX_GUESSES) {
                    // Send what it has already said. Without this the Worker's
                    // "do not repeat them" line has nothing to work with and the
                    // machine can return the same wrong word three times.
                    val reply = api.guess(
                        puzzleNumber = puzzle.number,
                        clue = submitted,
                        attempt = attempt,
                        previousGuesses = guesses.map { it.guess },
                        mode = mode,
                    )
                    guesses = guesses + MachineGuess(reply.guess, reply.correct, reply.confidence)
                    if (reply.correct) {
                        // Only the solving reply carries par — that is the call
                        // the server folded this round into.
                        par = reply.par; best = reply.best; solvers = reply.solvers
                        break
                    }
                    if (attempt < MAX_GUESSES) delay(700)
                }
                val result = RoundResult(
                    puzzleNumber = puzzle.number,
                    clue = submitted,
                    guesses = guesses,
                    solved = guesses.any { it.correct },
                    mode = mode,
                    elapsedMs = started.elapsedNow().inWholeMilliseconds,
                    par = par,
                    best = best,
                    solvers = solvers,
                )
                phase = Phase.Done(result)
                onFinished(result)
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                TextButton(onBack) { Text("Back") }
            } else {
                TextButton(onArchive) { Text("Archive") }
            }
            TextButton(onStats) { Text("Stats") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "MACHINE CHARADES #${puzzle.number}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats.currentStreak > 0) {
                Text(
                    "\u00b7  ${stats.currentStreak} day streak",
                    style = MaterialTheme.typography.labelLarge,
                    color = MachineGreen,
                )
            }
        }

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

        if (phase is Phase.Writing) {
            ModeRow(
                selected = mode,
                plus = plus,
                onSelect = { mode = it },
                onLocked = onWantPlus,
            )
        }

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

    // The answer arriving is the whole game. Mark it in the hand as well as on
    // the screen — a hit and a miss should not feel the same.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(guesses.size) {
        guesses.lastOrNull()?.let { latest ->
            haptics.performHapticFeedback(
                if (latest.correct) HapticFeedbackType.LongPress
                else HapticFeedbackType.TextHandleMove,
            )
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        guesses.forEachIndexed { i, g ->
            // Starts false and is flipped on first composition, so the row
            // actually animates in. `visible = true` never transitions, which
            // is why every guess used to snap into place.
            val appear = remember(i) {
                MutableTransitionState(false).apply { targetState = true }
            }
            AnimatedVisibility(
                visibleState = appear,
                enter = fadeIn(tween(320)) + expandVertically(tween(280)),
            ) {
                GuessRow(index = i + 1, guess = g)
            }
        }
        if (thinking && guesses.size < MAX_GUESSES) {
            ThinkingRow()
        }
    }
}

/**
 * The machine working.
 *
 * Three dots pulsing in sequence rather than a spinner: a spinner is what a
 * screen shows while it waits for a server, and this is a character taking its
 * turn. Same wait, different thing being communicated.
 */
@Composable
private fun ThinkingRow() {
    val pulse = rememberInfiniteTransition()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val alpha by pulse.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, delayMillis = i * 170),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
            Box(
                Modifier.padding(horizontal = 4.dp).size(8.dp).alpha(alpha)
                    .background(MachineGreen, CircleShape),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            "thinking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

            result.comparablePar?.let { par ->
                val saved = result.underPar ?: 0
                Text(
                    when {
                        saved > 0 -> "Par $par · you were $saved under"
                        saved == 0 -> "Par $par · you matched it"
                        else -> "Par $par · you were ${-saved} over"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (saved >= 0) MachineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                result.best?.let { best ->
                    Text(
                        "Shortest clue that has ever worked: $best",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

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

/**
 * The difficulty picker.
 *
 * Standard is always free. The three constraint modes are what Plus sells, and
 * they cost nothing to offer: ClueValidator already enforces all of them and
 * the Worker already re-checks them, so this row is the only part that was
 * ever missing.
 *
 * In a build with no store key the locked modes are hidden rather than shown
 * as locks — a padlock that opens an empty paywall reads as broken software.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeRow(
    selected: ConstraintMode,
    plus: Boolean,
    onSelect: (ConstraintMode) -> Unit,
    onLocked: () -> Unit,
) {
    val offered = if (plus || Plus.isConfigured) {
        ConstraintMode.entries
    } else {
        listOf(ConstraintMode.NONE)
    }
    if (offered.size == 1) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        offered.forEach { candidate ->
            val locked = candidate != ConstraintMode.NONE && !plus
            val active = candidate == selected
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = when {
                    active -> MachineGreen
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.clickable {
                    if (locked) onLocked() else onSelect(candidate)
                },
            ) {
                Text(
                    if (locked) "\uD83D\uDD12 " + Scoring.modeLabel(candidate)
                    else Scoring.modeLabel(candidate),
                    Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        active -> MaterialTheme.colorScheme.onPrimary
                        locked -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
