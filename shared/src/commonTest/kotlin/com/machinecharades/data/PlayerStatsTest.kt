package com.machinecharades.data

import com.machinecharades.core.MachineGuess
import com.machinecharades.core.RoundResult
import kotlin.test.Test
import com.machinecharades.data.Plus
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

private fun round(n: Int, solved: Boolean = true, clue: String = "yellow tree eater") =
    RoundResult(
        puzzleNumber = n,
        clue = clue,
        guesses = listOf(MachineGuess("giraffe", solved, 1f)),
        solved = solved,
    )

class PlayerStatsTest {

    @Test
    fun first_round_starts_a_streak_of_one() {
        val s = PlayerStats().recording(round(1))
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.maxStreak)
        assertEquals(1, s.played)
        assertEquals(1, s.lastPuzzleNumber)
    }

    @Test
    fun consecutive_puzzles_extend_the_streak() {
        val s = PlayerStats().recording(round(1)).recording(round(2)).recording(round(3))
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.maxStreak)
    }

    @Test
    fun a_skipped_day_resets_the_streak_but_keeps_the_best() {
        val s = PlayerStats()
            .recording(round(1)).recording(round(2)).recording(round(3))
            .recording(round(5))
        assertEquals(1, s.currentStreak)
        assertEquals(3, s.maxStreak, "the best run survives a break")
        assertEquals(4, s.played)
    }

    @Test
    fun replaying_a_stored_round_changes_nothing() {
        val once = PlayerStats().recording(round(1)).recording(round(2))
        val twice = once.recording(round(2))
        assertSame(once, twice, "a double submit must not inflate the streak")
    }

    @Test
    fun playing_the_archive_does_not_rewrite_the_run() {
        // Reached puzzle 5, then goes back and plays 2.
        val s = PlayerStats().recording(round(4)).recording(round(5)).recording(round(2))
        assertEquals(2, s.currentStreak, "an old puzzle counts for stats, not the run")
        assertEquals(5, s.lastPuzzleNumber)
        assertEquals(3, s.played)
    }

    @Test
    fun solve_rate_counts_only_solved_rounds() {
        val s = PlayerStats()
            .recording(round(1, solved = true))
            .recording(round(2, solved = false))
            .recording(round(3, solved = true))
            .recording(round(4, solved = true))
        assertEquals(4, s.played)
        assertEquals(3, s.solved)
        assertEquals(75, s.solveRate)
    }

    @Test
    fun solve_rate_is_zero_before_anything_is_played() {
        assertEquals(0, PlayerStats().solveRate)
    }

    @Test
    fun shortest_and_average_ignore_rounds_the_machine_never_solved() {
        // A failed round still has a clue, but it is not evidence of a short
        // clue that works — which is the only thing these two numbers claim.
        val s = PlayerStats()
            .recording(round(1, clue = "a very long clue indeed here"))          // 27, solved
            .recording(round(2, solved = false, clue = "x"))                     // 1, failed
            .recording(round(3, clue = "short one"))                             // 9, solved
        assertEquals(9, s.shortestClue)
        assertEquals(18, s.averageClueChars, "mean of 27 and 9, failures excluded")
    }

    @Test
    fun shortest_and_average_are_absent_before_a_first_solve() {
        assertNull(PlayerStats().shortestClue)
        assertNull(PlayerStats().averageClueChars)
        val onlyFailures = PlayerStats().recording(round(1, solved = false))
        assertNull(onlyFailures.shortestClue)
        assertNull(onlyFailures.averageClueChars)
    }

    @Test
    fun a_stored_round_is_recoverable_by_number() {
        val s = PlayerStats().recording(round(7, clue = "long spotty one"))
        assertNull(s.roundFor(6))
        val stored = assertNotNull(s.roundFor(7))
        assertEquals("long spotty one", stored.clue)
        assertEquals(7, stored.asResult().puzzleNumber)
    }
}

/** In-memory Storage, so the round trip is testable without a device. */
private class FakeStorage : Storage {
    private val map = mutableMapOf<String, String>()
    var writes = 0; private set
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value; writes++ }
}

class PlayerStoreTest {

    @Test
    fun stats_survive_a_save_and_load() {
        val storage = FakeStorage()
        val saved = PlayerStats().recording(round(1)).recording(round(2))
        PlayerStore(storage).save(saved)

        val loaded = PlayerStore(storage).load()
        assertEquals(saved, loaded)
        assertEquals(2, loaded.currentStreak)
        assertEquals("yellow tree eater", assertNotNull(loaded.roundFor(2)).clue)
    }

    @Test
    fun an_empty_store_loads_a_blank_slate() {
        assertEquals(PlayerStats(), PlayerStore(FakeStorage()).load())
    }

    @Test
    fun corrupt_json_loads_a_blank_slate_rather_than_throwing() {
        val storage = FakeStorage().also { it.put("player-stats", "{ not json") }
        assertEquals(PlayerStats(), PlayerStore(storage).load())
    }
}

class PlusGateTest {

    @Test
    fun a_build_with_no_store_shows_everything() {
        // A build that cannot sell must degrade to a complete free game rather
        // than to a game full of padlocks that open an empty sheet. Every gate
        // in the app asks this, so the branches must not drift.
        assertTrue(
            Plus.unlocked(entitled = false, configured = false),
            "nothing to buy means nothing to lock",
        )
        assertTrue(Plus.unlocked(entitled = true, configured = false))
    }

    @Test
    fun a_build_that_can_sell_locks_what_was_not_bought() {
        assertFalse(Plus.unlocked(entitled = false, configured = true))
        assertTrue(Plus.unlocked(entitled = true, configured = true))
    }

    @Test
    fun the_entitlement_identifier_is_the_one_the_dashboard_uses() {
        // Hardcoded on both sides. A mismatch makes every purchase succeed and
        // unlock nothing, with no error anywhere to explain it.
        assertEquals("plus", Plus.ENTITLEMENT)
    }
}
