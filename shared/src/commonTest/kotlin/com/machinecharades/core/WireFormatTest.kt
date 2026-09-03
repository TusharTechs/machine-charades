package com.machinecharades.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import com.machinecharades.net.wireName
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the JSON wire format of every @Serializable type in Model.kt.
 *
 * The `@SerialName` short keys are a contract with the Worker, not a cosmetic
 * choice — the puzzle document is fetched once per session and the keys were
 * shortened deliberately to keep it small. Renaming a Kotlin property is a
 * source-compatible change that silently breaks that contract: the field
 * quietly stops round-tripping and a puzzle loses its banned list.
 *
 * These assertions are the guard. If one fails because you intentionally
 * changed the format, change worker/src/ in the same commit — the server is
 * the other half of this contract.
 *
 * Deliberately kept out of CoreTest.kt, which mirrors the TypeScript suite in
 * worker/src/validator.test.ts one-for-one and should stay that way.
 */
class WireFormatTest {

    /** encodeDefaults so an omitted default can't hide a renamed key. */
    private val json = Json { encodeDefaults = true }

    @Test
    fun dailyPuzzleKeysAreStable() {
        val encoded = json.encodeToString(
            DailyPuzzle.serializer(),
            DailyPuzzle(
                number = 1,
                date = "2026-09-24",
                word = "giraffe",
                banned = listOf("neck", "tall"),
                allow = listOf("tally"),
                category = "animal",
                difficulty = 2,
            ),
        )
        assertEquals(
            """{"n":1,"date":"2026-09-24","word":"giraffe","banned":["neck","tall"],""" +
                """"allow":["tally"],"cat":"animal","diff":2}""",
            encoded,
        )
    }

    @Test
    fun constraintModeNamesAreStable() {
        // These strings are persisted in round history — renaming one silently
        // invalidates every stored round that used that mode.
        assertEquals("\"none\"", json.encodeToString(ConstraintMode.serializer(), ConstraintMode.NONE))
        assertEquals("\"no_vowels\"", json.encodeToString(ConstraintMode.serializer(), ConstraintMode.NO_VOWELS))
        assertEquals("\"one_word\"", json.encodeToString(ConstraintMode.serializer(), ConstraintMode.ONE_WORD))
        assertEquals("\"cap20\"", json.encodeToString(ConstraintMode.serializer(), ConstraintMode.TWENTY_CHAR_CAP))
    }

    @Test
    fun rejectionReasonNamesAreStable() {
        val expected = mapOf(
            ClueRejection.Reason.EMPTY to "empty",
            ClueRejection.Reason.TOO_LONG to "too_long",
            ClueRejection.Reason.CONTAINS_SECRET_WORD to "contains_word",
            ClueRejection.Reason.CONTAINS_BANNED_WORD to "contains_banned",
            ClueRejection.Reason.VOWEL_USED to "vowel",
            ClueRejection.Reason.MORE_THAN_ONE_WORD to "multi_word",
            ClueRejection.Reason.OVER_CHAR_CAP to "over_cap",
        )
        // Every enum constant must be covered, so adding one fails this test
        // rather than slipping through unasserted.
        assertEquals(ClueRejection.Reason.entries.size, expected.size)
        for ((reason, name) in expected) {
            assertEquals(
                "\"$name\"",
                json.encodeToString(ClueRejection.Reason.serializer(), reason),
            )
        }
    }

    @Test
    fun roundResultKeysAreStable() {
        val encoded = json.encodeToString(
            RoundResult.serializer(),
            RoundResult(
                puzzleNumber = 7,
                clue = "tall spotty",
                guesses = listOf(MachineGuess("zebra", correct = false, confidence = 0.25f)),
                solved = true,
                mode = ConstraintMode.NO_VOWELS,
                elapsedMs = 1234,
            ),
        )
        assertEquals(
            """{"n":7,"clue":"tall spotty","guesses":[{"guess":"zebra","correct":false,""" +
                """"conf":0.25}],"solved":true,"mode":"no_vowels","ms":1234}""",
            encoded,
        )
    }

    @Test
    fun pairingAndPartnerRoundKeysAreStable() {
        assertEquals(
            """{"id":"p1","name":"Sam","week":"2026-09-21","mine":3,"theirs":2}""",
            json.encodeToString(
                Pairing.serializer(),
                Pairing("p1", "Sam", "2026-09-21", myWins = 3, partnerWins = 2),
            ),
        )
        assertEquals(
            """{"n":142,"solved":true,"guesses":1,"chars":22,"clue":null}""",
            json.encodeToString(
                PartnerRound.serializer(),
                PartnerRound(142, solved = true, guessesUsed = 1, clueChars = 22),
            ),
        )
    }

    @Test
    fun everyTypeRoundTrips() {
        val puzzle = DailyPuzzle(1, "2026-09-24", "giraffe", listOf("neck"))
        assertEquals(puzzle, roundTrip(DailyPuzzle.serializer(), puzzle))

        val rejection = ClueRejection(ClueRejection.Reason.CONTAINS_BANNED_WORD, "necked")
        assertEquals(rejection, roundTrip(ClueRejection.serializer(), rejection))

        val result = RoundResult(
            puzzleNumber = 7,
            clue = "tall spotty",
            guesses = listOf(MachineGuess("giraffe", correct = true)),
            solved = true,
        )
        assertEquals(result, roundTrip(RoundResult.serializer(), result))
    }

    @Test
    fun unknownServerKeysDoNotBreakDecoding() {
        // The Worker will add fields before the app is updated. A shipped
        // binary must tolerate that rather than failing to load the puzzle.
        val lenient = Json { ignoreUnknownKeys = true }
        val withFutureField =
            """{"n":1,"date":"2026-09-24","word":"giraffe","banned":["neck"],"hint":"soon"}"""
        val decoded = lenient.decodeFromString(DailyPuzzle.serializer(), withFutureField)
        assertEquals("giraffe", decoded.word)
        assertTrue(decoded.allow.isEmpty(), "absent optional should fall back to its default")
    }

    private fun <T> roundTrip(
        serializer: kotlinx.serialization.KSerializer<T>,
        value: T,
    ): T = json.decodeFromString(serializer, json.encodeToString(serializer, value))
}

/**
 * The Worker's union type is `'NONE' | 'NO_VOWELS' | 'ONE_WORD' | 'TWENTY_CHAR_CAP'`
 * (worker/src/validator.ts). These names are a separate contract from the
 * @SerialName values above, which are storage, and the two must not be merged.
 */
class ConstraintModeWireTest {

    @Test
    fun every_mode_maps_to_the_workers_own_spelling() {
        assertEquals("NONE", ConstraintMode.NONE.wireName())
        assertEquals("NO_VOWELS", ConstraintMode.NO_VOWELS.wireName())
        assertEquals("ONE_WORD", ConstraintMode.ONE_WORD.wireName())
        assertEquals("TWENTY_CHAR_CAP", ConstraintMode.TWENTY_CHAR_CAP.wireName())
    }

    @Test
    fun no_mode_sends_its_storage_name_by_accident() {
        // A storage name reaching the Worker matches no case and silently
        // disables the constraint, so the two vocabularies must stay disjoint.
        val storage = setOf("none", "no_vowels", "one_word", "cap20")
        ConstraintMode.entries.forEach { mode ->
            assertTrue(
                mode.wireName() !in storage,
                "${mode.name} is sending its storage spelling to the Worker",
            )
        }
    }
}
