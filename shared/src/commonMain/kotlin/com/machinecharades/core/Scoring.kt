package com.machinecharades.core

import kotlin.math.pow

/**
 * Scoring, wordcraft rating, and the share string.
 *
 * Design intent: guesses dominate, characters matter meaningfully. A short
 * first-try clue should feel clearly better than a long first-try clue, but a
 * long first-try clue should still beat a short second-try one. The numbers
 * below are tuned to that ordering — verified by test.
 */
object Scoring {

    /** Points for solving on attempt 1, 2, 3. */
    private val guessBase = intArrayOf(1000, 600, 350)

    /** Characters you may spend before the brevity bonus runs out. */
    /**
     * Where the brevity bonus starts paying.
     *
     * Was 60, which measured as free: tools/measure-difficulty.mjs puts the
     * machine's first-guess win rate at 100% for a 60-character clue and 100%
     * again at 40. It only starts missing below about 30 — 90% at 25 chars,
     * 67% at 15 — so 30 is where earning something should begin.
     */
    const val CHAR_ALLOWANCE = 30

    /** Points per character saved under the allowance. */
    /**
     * Raised with the allowance so the reward still means something across a
     * much narrower band. At 5 a point a great clue beat a lazy one by about
     * 18%, which is not a scoreboard worth competing on; 15 across 30
     * characters puts a real gap between 25 chars and 12.
     */
    private const val CHAR_BONUS_PER = 15

    fun score(solved: Boolean, guessesUsed: Int, clueChars: Int): Int {
        if (!solved) return 0
        val idx = (guessesUsed - 1).coerceIn(0, guessBase.lastIndex)
        return guessBase[idx] + brevityBonus(clueChars)
    }

    /**
     * The part of the score that comes from brevity alone.
     *
     * Public because the clue field shows it while you type. Finding the
     * shortest clue that still lands is the actual skill of the game, and a
     * player who only meets this number on the results screen has already
     * finished the round without knowing what they were optimising for.
     */
    fun brevityBonus(clueChars: Int): Int =
        ((CHAR_ALLOWANCE - clueChars).coerceAtLeast(0)) * CHAR_BONUS_PER

    /**
     * Wordcraft rating update. Elo against the day's field: [fieldMeanScore] is
     * the mean score of everyone who played the same puzzle, sent down with the
     * next day's puzzle so it costs no extra request.
     *
     * K is deliberately low (24) so the rating is slow and therefore worth
     * something. A rating that swings 200 points on one bad day is not a
     * progression system, it's noise.
     */
    fun updateRating(
        current: Int,
        myScore: Int,
        fieldMeanScore: Int,
        k: Int = 24,
    ): Int {
        if (fieldMeanScore <= 0) return current
        // Actual result in 0..1: how you did against the field mean, squashed.
        val actual = (myScore.toDouble() / (myScore + fieldMeanScore).toDouble())
        // Expected result from the rating gap against a nominal field rating.
        val expected = 1.0 / (1.0 + 10.0.pow((FIELD_RATING - current) / 400.0))
        return (current + k * (actual - expected)).toInt().coerceAtLeast(FLOOR)
    }

    const val START_RATING = 1000
    private const val FIELD_RATING = 1000.0
    private const val FLOOR = 100

    /** Rating thresholds at which each constraint mode unlocks. */
    fun unlockedModes(rating: Int): List<ConstraintMode> = buildList {
        add(ConstraintMode.NONE)
        if (rating >= 1100) add(ConstraintMode.TWENTY_CHAR_CAP)
        if (rating >= 1250) add(ConstraintMode.ONE_WORD)
        if (rating >= 1400) add(ConstraintMode.NO_VOWELS)
    }

    /**
     * The share string. Spoiler-free: it reveals nothing about the word, only
     * how efficiently you got there. The character count is the part that
     * invites a challenge rather than just announcing a result.
     */
    /**
     * Where a shared result sends someone who wants to play.
     *
     * Wordle could share a bare grid because everyone already knew where to
     * find it. A game nobody has heard of cannot: without this every shared
     * result is a dead end, which is the difference between a score someone
     * admires and a game they open.
     *
     * One link for now. When the App Store listing exists this should become a
     * universal link that resolves per platform, rather than an iOS player
     * handing their friends a Play Store URL.
     */
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.techtush.machinecharades"

    fun shareString(
        result: RoundResult,
        partner: PartnerRound? = null,
        appName: String = "MACHINE CHARADES",
        url: String? = PLAY_URL,
    ): String = buildString {
        append(appName).append(" #").append(result.puzzleNumber).append('\n')
        append(squares(result)).append("  ")
        if (result.solved) {
            append(result.guessesUsed).append(if (result.guessesUsed == 1) " guess" else " guesses")
            append(" · ").append(result.clueChars).append(" chars")
        } else {
            append("stumped it")
        }
        if (result.mode != ConstraintMode.NONE) {
            append(" · ").append(modeLabel(result.mode))
        }
        // Par turns the share from an announcement into a challenge: a number
        // on its own says nothing, a number next to everyone else's is a dare.
        result.comparablePar?.let { par -> append(" · par ").append(par) }
        if (partner != null) {
            append("\nvs ")
            if (partner.solved) {
                append(partner.guessesUsed)
                    .append(if (partner.guessesUsed == 1) " guess" else " guesses")
                append(" · ").append(partner.clueChars).append(" chars")
            } else {
                append("they got stumped")
            }
        }
        url?.let { append('\n').append(it) }
    }

    /**
     * One square per attempt: red for a miss, green for the hit. A trailing
     * yellow marks an unused attempt you didn't need. Reads left to right as
     * the round actually unfolded.
     */
    private fun squares(result: RoundResult): String = buildString {
        result.guesses.forEach { append(if (it.correct) "🟩" else "🟥") }
        if (result.solved) {
            repeat(MAX_GUESSES - result.guessesUsed) { append("🟨") }
        }
    }

    fun modeLabel(mode: ConstraintMode): String = when (mode) {
        ConstraintMode.NONE -> "standard"
        ConstraintMode.NO_VOWELS -> "no vowels"
        ConstraintMode.ONE_WORD -> "one word"
        ConstraintMode.TWENTY_CHAR_CAP -> "20 chars"
    }
}
