package com.machinecharades.data

import com.machinecharades.core.MachineGuess
import com.machinecharades.core.RoundResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A round already played, kept so the day can be reopened rather than replayed. */
@Serializable
data class StoredRound(
    @SerialName("n") val puzzleNumber: Int,
    @SerialName("clue") val clue: String,
    @SerialName("guesses") val guesses: List<MachineGuess>,
    @SerialName("solved") val solved: Boolean,
    @SerialName("score") val score: Int,
    @SerialName("par") val par: Int? = null,
    @SerialName("best") val best: Int? = null,
    @SerialName("solvers") val solvers: Int = 0,
)

/**
 * Everything the game remembers about this player, on this device.
 *
 * Device-local on purpose. Syncing streaks across phones would mean real
 * accounts, and a real account obliges us to build account deletion and to
 * redo the Play data declarations. Not worth it for a daily word game.
 */
@Serializable
data class PlayerStats(
    /** Highest puzzle number played. Drives the streak; archive play can't raise it. */
    @SerialName("last") val lastPuzzleNumber: Int = 0,
    @SerialName("streak") val currentStreak: Int = 0,
    @SerialName("best") val maxStreak: Int = 0,
    @SerialName("played") val played: Int = 0,
    @SerialName("solved") val solved: Int = 0,
    @SerialName("total") val totalScore: Int = 0,
    @SerialName("history") val history: Map<Int, StoredRound> = emptyMap(),
) {
    fun roundFor(puzzleNumber: Int): StoredRound? = history[puzzleNumber]

    /** Rounds solved as a percentage, 0 when nothing has been played. */
    val solveRate: Int get() = if (played == 0) 0 else (solved * 100) / played

    /**
     * Folds a finished round in.
     *
     * Idempotent: replaying a stored day returns this unchanged, so a double
     * submit cannot inflate the streak or the totals.
     *
     * The streak counts consecutive days *played*, not won. The machine solves
     * most valid clues on its first attempt, so a win streak would be nearly
     * the same number with a crueller failure mode — and what the game actually
     * wants to reward is coming back.
     */
    fun recording(result: RoundResult): PlayerStats {
        if (history.containsKey(result.puzzleNumber)) return this

        // Only today's puzzle can extend a streak. Playing an old one from the
        // archive is worth stats, but must not rewrite the run.
        val extendsRun = result.puzzleNumber > lastPuzzleNumber
        val streak = when {
            !extendsRun -> currentStreak
            lastPuzzleNumber == result.puzzleNumber - 1 -> currentStreak + 1
            else -> 1
        }

        return copy(
            lastPuzzleNumber = if (extendsRun) result.puzzleNumber else lastPuzzleNumber,
            currentStreak = streak,
            maxStreak = maxOf(maxStreak, streak),
            played = played + 1,
            solved = solved + if (result.solved) 1 else 0,
            totalScore = totalScore + result.score,
            history = history + (
                result.puzzleNumber to StoredRound(
                    puzzleNumber = result.puzzleNumber,
                    clue = result.clue,
                    guesses = result.guesses,
                    solved = result.solved,
                    score = result.score,
                    par = result.par,
                    best = result.best,
                    solvers = result.solvers,
                )
                ),
        )
    }
}

/** Reads and writes [PlayerStats] through the platform store. */
class PlayerStore(private val storage: Storage = platformStorage()) {

    fun load(): PlayerStats {
        val raw = storage.get(KEY) ?: return PlayerStats()
        // A store written by a newer build, or half-written by a kill during
        // save, must not brick the game. Losing a streak is bad; a launch crash
        // that no reinstall fixes is worse.
        return try {
            json.decodeFromString(PlayerStats.serializer(), raw)
        } catch (_: Exception) {
            PlayerStats()
        }
    }

    fun save(stats: PlayerStats) {
        storage.put(KEY, json.encodeToString(PlayerStats.serializer(), stats))
    }

    private companion object {
        const val KEY = "player-stats"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

/**
 * Rebuilds the result screen from a stored round.
 *
 * Mode and elapsed time are not kept: the first is always NONE until constraint
 * modes ship, and the second only ever fed the round it was measured in.
 */
fun StoredRound.asResult(): RoundResult = RoundResult(
    puzzleNumber = puzzleNumber,
    clue = clue,
    guesses = guesses,
    solved = solved,
    par = par,
    best = best,
    solvers = solvers,
)
