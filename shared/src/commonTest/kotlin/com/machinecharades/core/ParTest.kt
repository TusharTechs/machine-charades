package com.machinecharades.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun solved(clue: String, par: Int? = null, best: Int? = null, solvers: Int = 0) =
    RoundResult(
        puzzleNumber = 1,
        clue = clue,
        guesses = listOf(MachineGuess("giraffe", true, 1f)),
        solved = true,
        par = par,
        best = best,
        solvers = solvers,
    )

class ParTest {

    @Test
    fun par_is_hidden_until_someone_else_has_played() {
        // A par computed from one solve is just your own score handed back,
        // which reads as a comparison and is not one.
        assertNull(solved("long yellow tree eater", par = 22, solvers = 1).comparablePar)
        assertEquals(18, solved("long yellow tree eater", par = 18, solvers = 2).comparablePar)
    }

    @Test
    fun par_is_hidden_when_the_server_sent_none() {
        assertNull(solved("long yellow tree eater", par = null, solvers = 9).comparablePar)
    }

    @Test
    fun under_par_counts_characters_saved() {
        val r = solved("short one", par = 18, solvers = 5)   // 9 chars
        assertEquals(9, r.underPar)
    }

    @Test
    fun over_par_is_negative() {
        val r = solved("a much longer clue than par", par = 12, solvers = 5)  // 27 chars
        assertEquals(-15, r.underPar)
    }

    @Test
    fun under_par_is_absent_when_par_is() {
        assertNull(solved("short one", par = 18, solvers = 1).underPar)
    }

    @Test
    fun share_string_carries_par_when_comparable() {
        val shared = Scoring.shareString(solved("short one", par = 18, solvers = 4))
        assertTrue("par 18" in shared, "share should invite a comparison: $shared")
    }

    @Test
    fun share_string_omits_par_from_a_lone_solve() {
        // Sharing "par 9" when you are the only solver claims a field that
        // does not exist yet.
        val shared = Scoring.shareString(solved("short one", par = 9, solvers = 1))
        assertTrue("par" !in shared, "share should not invent a field of one: $shared")
    }

    @Test
    fun share_string_stays_spoiler_free_with_par() {
        val shared = Scoring.shareString(solved("long yellow tree eater", par = 18, solvers = 4))
        assertTrue("giraffe" !in shared.lowercase(), "the guess must never leak")
        assertTrue("yellow" !in shared.lowercase(), "the clue must never leak")
    }
}

class ShareLinkTest {

    @Test
    fun the_share_carries_a_way_to_play() {
        // Without this every shared result is a dead end: the reader sees a
        // score and has nowhere to go. Wordle could omit it because everyone
        // already knew where it lived; a new game cannot.
        val shared = Scoring.shareString(
            RoundResult(
                puzzleNumber = 4,
                clue = "short one",
                guesses = listOf(MachineGuess("umbrella", true, 1f)),
                solved = true,
            ),
        )
        assertTrue(Scoring.PLAY_URL in shared, "share should link somewhere: $shared")
        assertTrue(shared.trimEnd().endsWith(Scoring.PLAY_URL), "the link belongs last")
    }

    @Test
    fun the_link_can_be_omitted_for_a_caller_that_supplies_its_own() {
        val shared = Scoring.shareString(
            RoundResult(
                puzzleNumber = 4,
                clue = "short one",
                guesses = listOf(MachineGuess("umbrella", true, 1f)),
                solved = true,
            ),
            url = null,
        )
        assertTrue("http" !in shared, "no link was asked for: $shared")
    }
}
