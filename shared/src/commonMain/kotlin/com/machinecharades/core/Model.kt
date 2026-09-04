package com.machinecharades.core

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One day's puzzle. Generated offline in batches and served as a single
 * document, so a normal session costs one read and zero model calls until the
 * player actually submits a clue.
 */
@Serializable
data class DailyPuzzle(
    /** Sequential puzzle number, shown to players and used in share strings. */
    @SerialName("n") val number: Int,
    /** ISO-8601 date, UTC. The day boundary is UTC for everyone, deliberately. */
    @SerialName("date") val date: String,
    /** The word the machine has to guess. Never leaves the device once shown. */
    @SerialName("word") val word: String,
    /** The obvious routes, blocked. Five is the tuned default. */
    @SerialName("banned") val banned: List<String>,
    /**
     * Tokens explicitly permitted, overriding the substring filter.
     *
     * The escape hatch for collisions the heuristic can't reason its way out
     * of — "blasting" contains "sting", "understand" starts with "under". Never
     * populate this by guessing; run tools/probe-validator.mjs, which measures
     * the false-positive rate against real puzzle content and prints the
     * candidates.
     */
    @SerialName("allow") val allow: List<String> = emptyList(),
    /** Optional flavour category shown under the word ("animal", "kitchen"). */
    @SerialName("cat") val category: String? = null,
    /** Difficulty 1..5, from the generator's own solve simulation. */
    @SerialName("diff") val difficulty: Int = 3,    /**
     * Median clue length among everyone who has solved this one, once there are
     * at least two of them. Absent on a puzzle nobody has finished.
     *
     * EncodeDefault.NEVER so a puzzle without par serialises exactly as it did
     * before par existed — WireFormatTest keeps meaning what it meant, and the
     * generated schedule documents stay byte-identical.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("par") val par: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("best") val best: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("solvers") val solvers: Int = 0,
)

/** Extra restriction on top of the banned list, unlocked by wordcraft rating. */
@Serializable
enum class ConstraintMode {
    @SerialName("none") NONE,
    @SerialName("no_vowels") NO_VOWELS,
    @SerialName("one_word") ONE_WORD,
    @SerialName("cap20") TWENTY_CHAR_CAP,
}

/** Why a clue was refused. The UI highlights [offendingToken] in the field. */
@Serializable
data class ClueRejection(
    @SerialName("reason") val reason: Reason,
    @SerialName("token") val offendingToken: String? = null,
) {
    @Serializable
    enum class Reason {
        @SerialName("empty") EMPTY,
        @SerialName("too_long") TOO_LONG,
        @SerialName("contains_word") CONTAINS_SECRET_WORD,
        @SerialName("contains_banned") CONTAINS_BANNED_WORD,
        @SerialName("vowel") VOWEL_USED,
        @SerialName("multi_word") MORE_THAN_ONE_WORD,
        @SerialName("over_cap") OVER_CHAR_CAP,
    }

    /** Player-facing copy. Says what went wrong and what to do about it. */
    fun message(): String = when (reason) {
        Reason.EMPTY -> "Write a clue first."
        Reason.TOO_LONG -> "Clues cap at $MAX_CLUE_CHARS characters. Trim it."
        Reason.CONTAINS_SECRET_WORD -> "That's the word itself. Go around it."
        Reason.CONTAINS_BANNED_WORD ->
            offendingToken?.let { "\"$it\" is blocked today. Try another angle." }
                ?: "That word is blocked today."
        Reason.VOWEL_USED -> "No vowels this round."
        Reason.MORE_THAN_ONE_WORD -> "One word only this round."
        Reason.OVER_CHAR_CAP -> "20 characters max this round."
    }
}

/** One machine attempt. [confidence] drives the reveal animation's weight. */
@Serializable
data class MachineGuess(
    @SerialName("guess") val guess: String,
    @SerialName("correct") val correct: Boolean,
    @SerialName("conf") val confidence: Float = 0.5f,
)

/** A completed round, local-first. Synced for the pair layer and leaderboards. */
@Serializable
data class RoundResult(
    @SerialName("n") val puzzleNumber: Int,
    @SerialName("clue") val clue: String,
    @SerialName("guesses") val guesses: List<MachineGuess>,
    @SerialName("solved") val solved: Boolean,
    @SerialName("mode") val mode: ConstraintMode = ConstraintMode.NONE,
    @SerialName("ms") val elapsedMs: Long = 0,
    /**
     * What everyone else spent, when the server had enough solves to say.
     *
     * EncodeDefault.NEVER so a round without par serialises exactly as it did
     * before par existed — every stored round stays byte-identical, and
     * WireFormatTest keeps meaning what it meant.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("par") val par: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("best") val best: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("solvers") val solvers: Int = 0,
) {
    /**
     * Par worth showing: your own solve is not a field to measure against, so
     * it stays hidden until someone else has played.
     */
    val comparablePar: Int? get() = par?.takeIf { solvers >= 2 }

    /** Characters saved against par. Negative means you spent more. */
    val underPar: Int? get() = comparablePar?.let { it - clueChars }

    val clueChars: Int get() = clue.trim().length
    val guessesUsed: Int get() = guesses.size
    val score: Int get() = Scoring.score(solved, guessesUsed, clueChars)
}

/** The weekly partner. One person, rotating. No guilds, no strangers. */
@Serializable
data class Pairing(
    @SerialName("id") val id: String,
    @SerialName("name") val partnerDisplayName: String,
    @SerialName("week") val weekStartDate: String,
    @SerialName("mine") val myWins: Int = 0,
    @SerialName("theirs") val partnerWins: Int = 0,
)

/**
 * What your partner did today. [clue] stays null until you've played — the
 * reveal is the whole social payload, so the server withholds it rather than
 * trusting the client to hide it.
 */
@Serializable
data class PartnerRound(
    @SerialName("n") val puzzleNumber: Int,
    @SerialName("solved") val solved: Boolean,
    @SerialName("guesses") val guessesUsed: Int,
    @SerialName("chars") val clueChars: Int,
    @SerialName("clue") val clue: String? = null,
)

/** Hard limit on clue length, before any constraint mode narrows it further. */
const val MAX_CLUE_CHARS = 120

/** Attempts the machine gets per round. */
const val MAX_GUESSES = 3
